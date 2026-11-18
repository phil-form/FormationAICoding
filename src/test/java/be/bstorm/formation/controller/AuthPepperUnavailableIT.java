package be.bstorm.formation.controller;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.crypto.InMemoryPasswordPepper;
import be.bstorm.formation.framework.crypto.PasswordPepper;
import be.bstorm.formation.framework.security.LoginRateLimiter;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.framework.security.password.Argon2PasswordHasher;
import be.bstorm.formation.support.AuthItSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature 001 — AC-SEC-12, variante {@code PasswordPepper} : OpenBao indisponible → 500 générique, aucun accès accordé,
 * pas d'oracle sur l'existence du compte. {@code @MockitoBean} en place dès le démarrage ; hachage d'alice pré-calculé
 * par un {@link Argon2PasswordHasher} distinct (décision unique de l'analyse).
 */
@Tag("feature-001")
@MockitoBean(types = PasswordPepper.class)
@MockitoSpyBean(types = LoginRateLimiter.class)
class AuthPepperUnavailableIT extends AuthItSupport {

    @BeforeEach
    void pepperUnavailable() {
        when(pepper.pepper(anyString())).thenThrow(new CryptoException("pepper unavailable"));
        when(pepper.pepper(anyString(), anyInt())).thenThrow(new CryptoException("pepper unavailable"));
    }

    @Test
    @DisplayName("[AC-SEC-12] Poivre en erreur, identifiants corrects : 500 avec errorId, sans cookie ni famille")
    void acsec12_pepperFailureOnLoginIsGeneric500WithoutSession() {
        // Given
        UUID alice = createAliceWithPrecomputedHash();

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertProblem(result, 500, "Internal Server Error", null);
        assertThat(result).bodyJson().extractingPath("$.errorId").isNotNull();
        assertNoSetCookie(result);
        assertThat(familyCount(alice)).isZero();
        verify(rateLimiter, never()).recordFailure(any());
    }

    @Test
    @DisplayName("[AC-SEC-12] Poivre en erreur, identifiant inconnu : même 500 (seul errorId diffère), aucun échec IP")
    void acsec12_pepperFailureForUnknownUserIsIndistinguishable() {
        // Given
        createAliceWithPrecomputedHash();

        // When
        MvcTestResult known = login("alice", PASSWORD);
        MvcTestResult unknown = login("nobody-here", PASSWORD);

        // Then
        assertProblem(unknown, 500, "Internal Server Error", null);
        assertThat(jsonString(unknown, "$.title")).isEqualTo(jsonString(known, "$.title"));
        assertThat(jsonString(unknown, "$.detail")).isEqualTo(jsonString(known, "$.detail"));
        assertThat(jsonString(unknown, "$.errorId")).isNotNull().isNotEqualTo(jsonString(known, "$.errorId"));
        verify(rateLimiter, never()).recordFailure(any());
    }

    private UUID createAliceWithPrecomputedHash() {
        String hash = new Argon2PasswordHasher(new InMemoryPasswordPepper(), authProperties).hash(PASSWORD);
        return createUserWithHash("alice", hash, Set.of(Role.DRIVER, Role.FLEET_MANAGER), DEPOT);
    }
}
