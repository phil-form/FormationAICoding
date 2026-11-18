package be.bstorm.formation.service.impl;

import be.bstorm.formation.dto.request.LoginRequest;
import be.bstorm.formation.dto.response.CurrentUserResponse;
import be.bstorm.formation.entity.RefreshToken;
import be.bstorm.formation.entity.RefreshTokenFamily;
import be.bstorm.formation.entity.SessionRevocationReason;
import be.bstorm.formation.entity.User;
import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.exception.InvalidSessionException;
import be.bstorm.formation.framework.exception.RetryLaterException;
import be.bstorm.formation.framework.exception.UnauthorizedException;
import be.bstorm.formation.framework.security.AuthProperties;
import be.bstorm.formation.framework.security.CurrentUser;
import be.bstorm.formation.framework.security.CurrentUserProvider;
import be.bstorm.formation.framework.security.LoginRateLimiter;
import be.bstorm.formation.framework.security.SecurityEvents;
import be.bstorm.formation.framework.security.jwt.AccessTokenIssuer;
import be.bstorm.formation.framework.security.password.PasswordHasher;
import be.bstorm.formation.mapper.AuthMapper;
import be.bstorm.formation.repository.RefreshTokenFamilyRepository;
import be.bstorm.formation.repository.RefreshTokenRepository;
import be.bstorm.formation.repository.UserRepository;
import be.bstorm.formation.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation de l'authentification (feature 001).
 * <p>
 * <b>Découpage transactionnel imposé</b> (SEC-11) : aucune transaction ni connexion DB n'est tenue pendant l'attente du
 * sémaphore, le poivre, Argon2 ou la signature Transit. {@code login} et {@code refresh} ne sont pas transactionnels :
 * ils enchaînent des blocs {@link TransactionTemplate} courts. Toutes les écritures sur des lignes existantes sont des
 * mises à jour conditionnelles sans incrément de {@code version} (RG-23). Les événements de sécurité sont journalisés
 * après le commit du bloc qui les décide (SEC-10).
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    static final String INVALID_CREDENTIALS = "Invalid credentials.";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenFamilyRepository familyRepository;
    private final RefreshTokenRepository tokenRepository;
    private final PasswordHasher passwordHasher;
    private final LoginRateLimiter rateLimiter;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthMapper authMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuthProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    private final SecureRandom secureRandom = new SecureRandom();

    private enum LoginOutcome { SUCCESS, SUCCESS_REHASHED, REFUSED }

    // ------------------------------------------------------------------------------------------------ login (RG-24)

    @Override
    public IssuedTokens login(LoginRequest request, String clientIp) {
        // 1. Limitation par IP (mémoire) : ni recherche, ni hachage.
        Optional<Duration> blocked = rateLimiter.blockedFor(clientIp);
        if (blocked.isPresent()) {
            SecurityEvents.warn("LOGIN_RATE_LIMITED", null, clientIp, null, null, null);
            throw new RetryLaterException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    "Too many failed login attempts. Retry later.", blocked.get());
        }

        // 2. Normalisation et recherche (transaction en lecture du repository, rôles chargés).
        String rawPassword = request.password();
        User user = userRepository.findByUsername(normalize(request.username())).orElse(null);

        // 3. Inconnu / désactivé / sans rôle / verrouillé : même coût et mêmes dépendances que verify.
        String precheck = precheckFailure(user);
        if (precheck != null) {
            passwordHasher.dummyVerify(rawPassword);
            throw loginFailure(user == null ? null : user.getId(), clientIp, precheck);
        }

        UUID userId = user.getId();
        String verifiedHash = user.getPasswordHash();
        PasswordHasher.Verification verification = passwordHasher.verify(rawPassword, verifiedHash);

        // 4. Mot de passe faux.
        if (verification == PasswordHasher.Verification.MISMATCH) {
            throw badPassword(userId, clientIp);
        }

        // 5. Succès vérifié : now lu une seule fois.
        Instant now = clock.instant();
        String newHash = null;
        if (verification == PasswordHasher.Verification.MATCH_REHASH_NEEDED) {
            try {
                newHash = passwordHasher.hash(rawPassword);
            } catch (CryptoException | RetryLaterException e) {
                SecurityEvents.warn("PASSWORD_REHASH_FAILED", userId, clientIp, null, null, null);
            }
        }

        // 6. Hors transaction : famille (id attribué d'avance = sid), jeton de rafraîchissement, jeton d'accès signé.
        AuthProperties.Refresh refresh = properties.refresh();
        RefreshTokenFamily family = RefreshTokenFamily.builder()
                .absoluteExpiresAt(now.plus(refresh.absoluteTtl()))
                .build();
        UUID familyId = family.assignId();
        String refreshToken = newRefreshToken();
        Instant tokenExpiresAt = earliest(now.plus(refresh.idleTtl()), family.getAbsoluteExpiresAt());
        CurrentUser currentUser = authMapper.toCurrentUser(user);
        String accessToken = accessTokenIssuer.issue(currentUser, familyId, now);

        // 7. Transaction courte : écriture conditionnelle de succès en premier (RG-25).
        String rehash = newHash;
        LoginOutcome outcome = transactionTemplate.execute(status -> {
            if (userRepository.registerSuccessfulLogin(userId, verifiedHash, now) == 0) {
                status.setRollbackOnly();
                return LoginOutcome.REFUSED;
            }
            boolean rehashed = rehash != null
                    && userRepository.updatePasswordHash(userId, verifiedHash, rehash, now) == 1;
            family.setUser(userRepository.getReferenceById(userId));
            familyRepository.save(family);
            tokenRepository.save(RefreshToken.builder()
                    .family(family)
                    .tokenHash(sha256Hex(refreshToken))
                    .expiresAt(tokenExpiresAt)
                    .build());
            return rehashed ? LoginOutcome.SUCCESS_REHASHED : LoginOutcome.SUCCESS;
        });

        // 8. Après la sortie du bloc.
        if (outcome == null || outcome == LoginOutcome.REFUSED) {
            throw loginFailure(userId, clientIp, "CREDENTIALS_CHANGED");
        }
        SecurityEvents.info("LOGIN_SUCCESS", userId, clientIp, familyId, null, null);
        if (outcome == LoginOutcome.SUCCESS_REHASHED) {
            SecurityEvents.info("PASSWORD_REHASHED", userId, clientIp, null, null, null);
        }
        return new IssuedTokens(
                authMapper.toTokenResponse(accessToken, accessTokenIssuer.ttlSeconds(), currentUser),
                refreshToken,
                Duration.between(now, tokenExpiresAt));
    }

    /** Priorité des causes (SEC-10) : DISABLED > NO_ROLE > LOCKED ; null si le mot de passe doit être vérifié. */
    private String precheckFailure(User user) {
        if (user == null) {
            return "UNKNOWN_USER";
        }
        if (!user.isEnabled()) {
            return "DISABLED";
        }
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return "NO_ROLE";
        }
        Instant lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && clock.instant().isBefore(lockedUntil)) {
            return "LOCKED";
        }
        return null;
    }

    /** RG-24 étape 4 : commit de l'écriture d'échec → LOGIN_FAILURE → ACCOUNT_LOCKED (si posé) → échec IP → 401. */
    private UnauthorizedException badPassword(UUID userId, String clientIp) {
        Instant now = clock.instant();
        AuthProperties.Login login = properties.login();
        Boolean lockedNow = transactionTemplate.execute(status -> {
            int updated = userRepository.registerFailedLogin(userId, now, login.maxFailedAttempts(),
                    now.plus(login.lockDuration()));
            return updated == 1 && userRepository.findLockedUntilById(userId) != null;
        });
        SecurityEvents.info("LOGIN_FAILURE", userId, clientIp, null, "BAD_PASSWORD", null);
        if (Boolean.TRUE.equals(lockedNow)) {
            SecurityEvents.warn("ACCOUNT_LOCKED", userId, clientIp, null, null, null);
        }
        rateLimiter.recordFailure(clientIp);
        return new UnauthorizedException(INVALID_CREDENTIALS);
    }

    /** LOGIN_FAILURE → échec IP → 401 (hors transaction). */
    private UnauthorizedException loginFailure(UUID userId, String clientIp, String reason) {
        SecurityEvents.info("LOGIN_FAILURE", userId, clientIp, null, reason, null);
        rateLimiter.recordFailure(clientIp);
        return new UnauthorizedException(INVALID_CREDENTIALS);
    }

    // ---------------------------------------------------------------------------------------------- refresh (RG-22)

    /** Décision de la phase A du refresh. */
    private record RefreshCheck(String rejectReason, boolean reuse, UUID userId, UUID familyId, UUID tokenId,
                                Instant absoluteExpiresAt, CurrentUser user) {

        static RefreshCheck rejected(String reason, UUID userId, UUID familyId) {
            return new RefreshCheck(reason, false, userId, familyId, null, null, null);
        }

        static RefreshCheck reused(UUID userId, UUID familyId) {
            return new RefreshCheck(null, true, userId, familyId, null, null, null);
        }

        boolean accepted() {
            return rejectReason == null && !reuse;
        }
    }

    @Override
    public IssuedTokens refresh(String refreshToken, String clientIp) {
        Instant now = clock.instant();

        // 1. Cookie absent ou vide.
        if (refreshToken == null || refreshToken.isBlank()) {
            SecurityEvents.info("REFRESH_REJECTED", null, clientIp, null, "NO_COOKIE", null);
            throw new InvalidSessionException();
        }
        String tokenHash = sha256Hex(refreshToken);

        // Phase A : étapes 2 à 7, révocations validées avant la levée du 401.
        RefreshCheck check = transactionTemplate.execute(status -> checkRefresh(tokenHash, now));
        if (check == null) {
            throw new InvalidSessionException();
        }
        if (check.reuse()) {
            SecurityEvents.warn("REFRESH_REUSE_DETECTED", check.userId(), clientIp, check.familyId(), null, null);
            throw new InvalidSessionException();
        }
        if (!check.accepted()) {
            SecurityEvents.info("REFRESH_REJECTED", check.userId(), clientIp, check.familyId(), check.rejectReason(),
                    null);
            throw new InvalidSessionException();
        }

        // Phase B : signature hors transaction (étape 8) ; une exception → 500, aucune écriture.
        String accessToken = accessTokenIssuer.issue(check.user(), check.familyId(), now);

        // Phase C : consommation atomique puis nouveau jeton (étapes 9 et 10).
        String newRefreshToken = newRefreshToken();
        Instant newExpiresAt = earliest(now.plus(properties.refresh().idleTtl()), check.absoluteExpiresAt());
        Boolean consumed = transactionTemplate.execute(status -> {
            if (tokenRepository.markUsed(check.tokenId(), now) == 0) {
                familyRepository.revoke(check.familyId(), SessionRevocationReason.REUSE_DETECTED, now);
                return false;
            }
            tokenRepository.save(RefreshToken.builder()
                    .family(familyRepository.getReferenceById(check.familyId()))
                    .tokenHash(sha256Hex(newRefreshToken))
                    .expiresAt(newExpiresAt)
                    .build());
            return true;
        });
        if (!Boolean.TRUE.equals(consumed)) {
            SecurityEvents.warn("REFRESH_REUSE_DETECTED", check.userId(), clientIp, check.familyId(), null, null);
            throw new InvalidSessionException();
        }
        SecurityEvents.info("REFRESH_SUCCESS", check.userId(), clientIp, check.familyId(), null, null);
        return new IssuedTokens(
                authMapper.toTokenResponse(accessToken, accessTokenIssuer.ttlSeconds(), check.user()),
                newRefreshToken,
                Duration.between(now, newExpiresAt));
    }

    /** RG-22 étapes 2 à 7, dans la transaction de la phase A. Aucune journalisation ici. */
    private RefreshCheck checkRefresh(String tokenHash, Instant now) {
        Optional<RefreshToken> found = tokenRepository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return RefreshCheck.rejected("UNKNOWN_TOKEN", null, null);
        }
        RefreshToken token = found.get();
        RefreshTokenFamily family = token.getFamily();
        UUID familyId = family.getId();
        UUID userId = family.getUser().getId();   // id d'un proxy : aucune initialisation de l'utilisateur

        if (family.getRevokedAt() != null) {
            return RefreshCheck.rejected("FAMILY_REVOKED", userId, familyId);
        }
        if (!now.isBefore(family.getAbsoluteExpiresAt())) {
            return RefreshCheck.rejected("FAMILY_EXPIRED", userId, familyId);
        }
        if (token.getUsedAt() != null) {
            familyRepository.revoke(familyId, SessionRevocationReason.REUSE_DETECTED, now);
            return RefreshCheck.reused(userId, familyId);
        }
        if (!now.isBefore(token.getExpiresAt())) {
            return RefreshCheck.rejected("TOKEN_EXPIRED", userId, familyId);
        }
        Optional<User> user = userRepository.findWithRolesById(userId);
        if (user.isEmpty() || !user.get().isEnabled() || user.get().getRoles() == null
                || user.get().getRoles().isEmpty()) {
            familyRepository.revoke(familyId, SessionRevocationReason.USER_DISABLED, now);
            return RefreshCheck.rejected("USER_DISABLED", userId, familyId);
        }
        CurrentUser currentUser = authMapper.toCurrentUser(user.get());   // rôles lus dans ce bloc
        return new RefreshCheck(null, false, userId, familyId, token.getId(), family.getAbsoluteExpiresAt(),
                currentUser);
    }

    // ----------------------------------------------------------------------------------------------- logout (RG-12)

    @Override
    @Transactional
    public void logout(String refreshToken, String clientIp) {
        UUID userId = null;
        UUID familyId = null;
        if (refreshToken != null && !refreshToken.isEmpty()) {
            Optional<RefreshToken> token = tokenRepository.findByTokenHash(sha256Hex(refreshToken));
            if (token.isPresent()) {
                RefreshTokenFamily family = token.get().getFamily();
                familyId = family.getId();
                userId = family.getUser().getId();
                familyRepository.revoke(familyId, SessionRevocationReason.LOGOUT, clock.instant());
            }
        }
        UUID loggedUser = userId;
        UUID loggedFamily = familyId;
        afterCommit(() -> SecurityEvents.info("LOGOUT", loggedUser, clientIp, loggedFamily, null, null));
    }

    // ------------------------------------------------------------------------------------- révocation globale (RG-16)

    @Override
    @Transactional
    public void revokeAllSessions(UUID userId, SessionRevocationReason reason) {
        // flushAutomatically = true : les modifications en attente de la transaction englobante partent d'abord.
        int count = familyRepository.revokeAllActiveByUserId(userId, reason, clock.instant());
        afterCommit(() -> SecurityEvents.info("SESSIONS_REVOKED", userId, null, null, reason.name(), count));
    }

    // ------------------------------------------------------------------------------------------------ purge (RG-17)

    @Override
    @Transactional
    public int purgeExpiredSessions() {
        Instant threshold = clock.instant().minus(properties.refresh().purgeRetention());
        tokenRepository.deleteOfPurgeableFamilies(threshold);
        return familyRepository.deletePurgeable(threshold);
    }

    // ------------------------------------------------------------------------------------------ utilisateur courant

    @Override
    public CurrentUserResponse currentUser() {
        return authMapper.toResponse(currentUserProvider.require());
    }

    // ------------------------------------------------------------------------------------------------------ outils

    /** RG-02 : trim puis minuscules (Locale.ROOT). */
    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** RG-08 : 32 octets aléatoires, base64url sans remplissage (43 caractères). */
    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** RG-08 : SHA-256 des octets US-ASCII de la valeur du cookie, hex minuscule (64 caractères). */
    static String sha256Hex(String cookieValue) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(cookieValue.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * Journalisation au commit de la transaction physique englobante (jamais en cas d'annulation) ; sans
     * synchronisation active (cas défensif), immédiate.
     */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
