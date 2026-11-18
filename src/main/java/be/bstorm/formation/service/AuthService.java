package be.bstorm.formation.service;

import be.bstorm.formation.dto.request.LoginRequest;
import be.bstorm.formation.dto.response.CurrentUserResponse;
import be.bstorm.formation.dto.response.TokenResponse;
import be.bstorm.formation.entity.SessionRevocationReason;

import java.time.Duration;
import java.util.UUID;

/** Authentification : login, refresh, logout, révocation globale, purge (feature 001). */
public interface AuthService {

    /**
     * RG-01..05, RG-14, RG-15, RG-24, RG-25. @throws UnauthorizedException (401 "Invalid credentials."),
     * RetryLaterException 429 (RG-05) ou 503 (RG-21), CryptoException (500).
     */
    IssuedTokens login(LoginRequest request, String clientIp);

    /**
     * RG-09..11, RG-15, RG-22. refreshToken null/vide ou tout refus → InvalidSessionException
     * (401 "Invalid or expired session.", cookie effacé par GlobalExceptionHandler).
     */
    IssuedTokens refresh(String refreshToken, String clientIp);

    /** RG-12, idempotent, ne lève jamais d'exception métier (refreshToken null accepté). */
    void logout(String refreshToken, String clientIp);

    /**
     * RG-16, pour la feature 002. Idempotent. Envoie d'abord en base les modifications en attente de la transaction
     * englobante ; peut donc propager les exceptions de ce flush (ex. {@code ObjectOptimisticLockingFailureException}) :
     * la transaction est alors annulée et rien n'est journalisé.
     */
    void revokeAllSessions(UUID userId, SessionRevocationReason reason);

    /** RG-17. @return nombre de familles supprimées. */
    int purgeExpiredSessions();

    /** RG-19 : utilisateur courant (CurrentUserProvider.require()) mappé, sans accès base ni transaction. */
    CurrentUserResponse currentUser();

    /**
     * @param refreshTtl Max-Age du cookie = expires_at du jeton émis − now, arrondi inférieur à la seconde (RG-08) ;
     *                   PAS le temps restant de la famille
     */
    record IssuedTokens(TokenResponse body, String refreshToken, Duration refreshTtl) {

        @Override
        public String toString() {
            return "IssuedTokens[refreshToken=***, refreshTtl=" + refreshTtl + "]";
        }
    }
}
