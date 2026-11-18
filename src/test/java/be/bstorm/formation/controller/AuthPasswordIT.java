package be.bstorm.formation.controller;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.exception.RetryLaterException;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.framework.security.password.Argon2PasswordHasher;
import be.bstorm.formation.support.SpiedAuthItSupport;
import be.bstorm.formation.support.TestAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Feature 001 — hachage des mots de passe (RG-14, RG-23, RG-24) : AC-15 à AC-17, AC-51, AC-55, AC-62, AC-64, AC-SEC-01.
 * Le poivre et la clé de signature sont partagés par le contexte : les versions sont comparées en relatif
 * (version de création {@code v}, puis {@code v + 1} après rotation).
 */
@Tag("feature-001")
class AuthPasswordIT extends SpiedAuthItSupport {

    @Test
    @DisplayName("[AC-15] password_hash = p<N>$ + Argon2id m=65536,t=3,p=1, sans le mot de passe, salé")
    void ac15_storedHashFormat() {
        // Given
        UUID alice = createAlice();

        // When
        String stored = passwordHash(alice);

        // Then
        assertThat(stored)
                .matches("^p\\d+\\$\\$argon2id\\$v=19\\$m=65536,t=3,p=1\\$.+\\$.+$")
                .doesNotContain(PASSWORD);
        assertThat(passwordHasher.hash(PASSWORD)).isNotEqualTo(passwordHasher.hash(PASSWORD));
    }

    @Test
    @DisplayName("[AC-16] Après rotation du poivre, un login réussi re-hache avec la nouvelle version")
    void ac16_loginRehashesWithNewPepperVersion() {
        // Given
        UUID alice = createAlice();
        int version = pepperVersion(passwordHash(alice));
        rotatePepper();

        // When
        MvcTestResult first = login("alice", PASSWORD);

        // Then
        assertThat(first).hasStatusOk();
        assertThat(passwordHash(alice)).startsWith("p" + (version + 1) + "$");
        assertThat(login("alice", PASSWORD)).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-17] Hachage aux paramètres Argon2 inférieurs : re-haché aux paramètres courants au login")
    void ac17_weakerArgon2ParametersAreUpgraded() {
        // Given
        String weakHash = new Argon2PasswordHasher(pepper,
                TestAuthProperties.withPassword(authProperties, 19_456, 2, 4, Duration.ofSeconds(2))).hash(PASSWORD);
        assertThat(weakHash).contains("m=19456,t=2");
        UUID alice = createUserWithHash("alice", weakHash, Set.of(Role.DRIVER), DEPOT);

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
        assertThat(passwordHash(alice)).contains("m=65536,t=3");
    }

    @Test
    @DisplayName("[AC-17] Hachage déjà à jour : inchangé après un login réussi")
    void ac17_upToDateHashIsUnchanged() {
        // Given
        UUID alice = createAlice();
        String before = passwordHash(alice);

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
        assertThat(passwordHash(alice)).isEqualTo(before);
    }

    @Test
    @DisplayName("[AC-51] Aucune écriture d'authentification n'incrémente la colonne version")
    void ac51_authenticationWritesDoNotIncrementVersion() {
        // Given
        UUID alice = createAlice();
        long userVersion = userVersion(alice);

        // When
        assertThat(login("alice", WRONG_PASSWORD)).hasStatus(401);
        rotatePepper();
        Session login = loginOk("alice", PASSWORD);
        UUID family = familyOf(login.cookie());
        long familyVersion = familyVersion(family);
        long firstTokenVersion = tokenVersion(login.cookie());
        Session refreshed = refreshOk(login.cookie());
        long secondTokenVersion = tokenVersion(refreshed.cookie());
        assertThat(logout(refreshed.cookie())).hasStatus(204);

        // Then
        assertThat(userVersion(alice)).isEqualTo(userVersion);
        assertThat(familyVersion(family)).isEqualTo(familyVersion);
        assertThat(tokenVersion(login.cookie())).isEqualTo(firstTokenVersion);
        assertThat(tokenVersion(refreshed.cookie())).isEqualTo(secondTokenVersion);
    }

    @Test
    @DisplayName("[AC-55] Échec du re-hachage : le login réussit et l'ancien hachage est conservé")
    void ac55_rehashFailureDoesNotBlockLogin() {
        // Given
        UUID alice = createAlice();
        String before = passwordHash(alice);
        int version = pepperVersion(before);
        rotatePepper();
        doThrow(new CryptoException("pepper unavailable")).when(passwordHasher).hash(anyString());

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
        assertThat(passwordHash(alice)).isEqualTo(before).startsWith("p" + version + "$");
    }

    @Test
    @DisplayName("[AC-62] Échec de signature au login : 500, aucune écriture, aucun échec IP ; puis login normal")
    void ac62_signingFailureOnLoginWritesNothing() {
        // Given
        UUID alice = createAlice();
        setFailedLoginCount(alice, 3);
        String h1 = passwordHash(alice);
        int version = pepperVersion(h1);
        rotatePepper();
        doThrow(new CryptoException("signing unavailable")).when(jwtKeys).sign(anyString(), any(byte[].class));

        // When
        MvcTestResult failed = login("alice", PASSWORD);

        // Then
        assertProblem(failed, 500, "Internal Server Error", null);
        assertNoSetCookie(failed);
        assertThat(failedLoginCount(alice)).isEqualTo(3);
        assertThat(lastLoginAt(alice)).isNull();
        assertThat(passwordHash(alice)).isEqualTo(h1).startsWith("p" + version + "$");
        assertThat(familyCount(alice)).isZero();
        assertThat(tokenCountForUser(alice)).isZero();
        verify(rateLimiter, never()).recordFailure(any());

        // When
        Mockito.reset(jwtKeys);
        MvcTestResult retried = login("alice", PASSWORD);

        // Then
        assertThat(retried).hasStatusOk();
        assertThat(passwordHash(alice)).startsWith("p" + (version + 1) + "$");
    }

    @Test
    @DisplayName("[AC-64] Poivre en erreur (CryptoException) sur un mauvais mot de passe : 500, aucun échec compté")
    void ac64_pepperCryptoFailureCountsNothing() {
        // Given
        UUID alice = createAlice();
        CryptoException failure = new CryptoException("pepper unavailable");
        doThrow(failure).when(pepper).pepper(anyString());
        doThrow(failure).when(pepper).pepper(anyString(), anyInt());

        // When
        MvcTestResult result = login("alice", WRONG_PASSWORD);

        // Then
        assertThat(result).hasStatus(500);
        assertThat(failedLoginCount(alice)).isZero();
        assertThat(lockedUntil(alice)).isNull();
        verify(rateLimiter, never()).recordFailure(any());
    }

    @Test
    @DisplayName("[AC-64] Poivre saturé (RetryLaterException 503) sur un mauvais mot de passe : 503, aucun échec compté")
    void ac64_pepperRetryLaterCountsNothing() {
        // Given
        UUID alice = createAlice();
        RetryLaterException busy = new RetryLaterException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "The service is busy. Retry later.", Duration.ofSeconds(1));
        doThrow(busy).when(pepper).pepper(anyString());
        doThrow(busy).when(pepper).pepper(anyString(), anyInt());

        // When
        MvcTestResult result = login("alice", WRONG_PASSWORD);

        // Then
        assertThat(result).hasStatus(503);
        assertThat(failedLoginCount(alice)).isZero();
        assertThat(lockedUntil(alice)).isNull();
        verify(rateLimiter, never()).recordFailure(any());
    }

    @Test
    @DisplayName("[AC-SEC-01] Le hachage ne contient ni le mot de passe ni son encodage base64")
    void acsec01_storedHashRevealsNothing() {
        // Given
        UUID alice = createAlice();
        byte[] raw = PASSWORD.getBytes(StandardCharsets.UTF_8);

        // When
        String stored = passwordHash(alice);

        // Then
        assertThat(stored).matches("^p\\d+\\$\\$argon2id\\$.*")
                .doesNotContain(PASSWORD)
                .doesNotContain(Base64.getEncoder().encodeToString(raw))
                .doesNotContain(Base64.getEncoder().withoutPadding().encodeToString(raw))
                .doesNotContain(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
    }

    @Test
    @DisplayName("[AC-SEC-01] Un hachage Argon2 calculé sans poivre ne permet pas de se connecter")
    void acsec01_unpepperedHashIsRejected() {
        // Given
        UUID alice = createAlice();
        int version = pepperVersion(passwordHash(alice));
        String unpeppered = "p" + version + "$" + new Argon2PasswordEncoder(16, 32, 1, 65_536, 3).encode(PASSWORD);
        setPasswordHash(alice, unpeppered);

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertInvalidCredentials(result);
    }
}
