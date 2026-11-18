package be.bstorm.formation.controller;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.security.jwt.JwtKeyProvider;
import be.bstorm.formation.support.AuthItSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Feature 001 — AC-SEC-12, variante {@code JwtKeyProvider.sign} : signature impossible → 500, aucune session. */
@Tag("feature-001")
@MockitoBean(types = JwtKeyProvider.class)
class AuthSigningUnavailableIT extends AuthItSupport {

    @BeforeEach
    void signingUnavailable() {
        when(jwtKeys.currentKeyId()).thenReturn("1");
        when(jwtKeys.sign(anyString(), any(byte[].class))).thenThrow(new CryptoException("signing unavailable"));
    }

    @Test
    @DisplayName("[AC-SEC-12] Signature en erreur, identifiants corrects : 500 avec errorId, sans cookie ni famille")
    void acsec12_signingFailureOnLoginIsGeneric500WithoutSession() {
        // Given
        UUID alice = createAlice();

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertProblem(result, 500, "Internal Server Error", null);
        assertThat(result).bodyJson().extractingPath("$.errorId").isNotNull();
        assertNoSetCookie(result);
        assertThat(familyCount(alice)).isZero();
    }
}
