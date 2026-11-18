package be.bstorm.formation.controller;

import be.bstorm.formation.support.SpiedAuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 001 — logout (RG-12) et anti-CSRF des routes à cookie (RG-13) : AC-35 à AC-39. */
@Tag("feature-001")
class AuthLogoutCsrfIT extends SpiedAuthItSupport {

    private static final String EVIL_ORIGIN = "https://evil.example";

    @Test
    @DisplayName("[AC-35] Logout : 204, cookie effacé, seule la famille du cookie est révoquée (LOGOUT)")
    void ac35_logoutRevokesOnlyTheCookieFamily() {
        // Given
        createAlice();
        Session a = loginOk("alice", PASSWORD);
        Session x = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = logout(a.cookie());

        // Then
        assertThat(result).hasStatus(204);
        assertThat(body(result)).isEmpty();
        List<SetCookie> cookies = refreshCookies(result);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.getFirst().attr("Max-Age")).isEqualTo("0");
        assertThat(cookies.getFirst().attr("Path")).isEqualTo("/api/v1/auth");
        assertThat(revocationReason(familyOf(a.cookie()))).isEqualTo("LOGOUT");
        assertThat(refresh(a.cookie())).hasStatus(401);
        assertThat(refresh(x.cookie())).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-36] Logout sans cookie ou avec un cookie inconnu : 204 et cookie effacé")
    void ac36_logoutIsIdempotentWithoutKnownCookie() {
        // When
        MvcTestResult withoutCookie = logout(null);
        MvcTestResult unknownCookie = logout(UNKNOWN_REFRESH_TOKEN);

        // Then
        assertThat(withoutCookie).hasStatus(204);
        assertRefreshCookieCleared(withoutCookie);
        assertThat(unknownCookie).hasStatus(204);
        assertRefreshCookieCleared(unknownCookie);
    }

    @Test
    @DisplayName("[AC-36] Logout d'une famille déjà révoquée pour REUSE_DETECTED : 204, raison conservée")
    void ac36_logoutKeepsExistingRevocationReason() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        Session refreshed = refreshOk(login.cookie());
        assertThat(refresh(login.cookie())).hasStatus(401);
        UUID family = familyOf(login.cookie());
        assertThat(revocationReason(family)).isEqualTo("REUSE_DETECTED");

        // When
        MvcTestResult result = logout(refreshed.cookie());

        // Then
        assertThat(result).hasStatus(204);
        assertThat(revocationReason(family)).isEqualTo("REUSE_DETECTED");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REFRESH, LOGOUT})
    @DisplayName("[AC-37] Sans X-Requested-With (Origin autorisée) : 403, session intacte")
    void ac37_missingRequestedWithIsRejected(String route) {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = postWithHeaders(route, login.cookie(), ALLOWED_ORIGIN, null);

        // Then
        assertCsrfRejected(result);
        assertSessionUntouched(login.cookie());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REFRESH, LOGOUT})
    @DisplayName("[AC-37] X-Requested-With vide (après trim) : 403, session intacte")
    void ac37_blankRequestedWithIsRejected(String route) {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = postWithHeaders(route, login.cookie(), ALLOWED_ORIGIN, "   ");

        // Then
        assertCsrfRejected(result);
        assertSessionUntouched(login.cookie());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REFRESH, LOGOUT})
    @DisplayName("[AC-38] Sans en-tête Origin : 403 ProblemDetail, session intacte")
    void ac38_missingOriginIsRejected(String route) {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = postWithHeaders(route, login.cookie(), null, "XMLHttpRequest");

        // Then
        assertCsrfRejected(result);
        assertSessionUntouched(login.cookie());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REFRESH, LOGOUT})
    @DisplayName("[AC-38] Origin: null : 403 ProblemDetail, session intacte")
    void ac38_nullOriginIsRejected(String route) {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = postWithHeaders(route, login.cookie(), "null", "XMLHttpRequest");

        // Then
        assertCsrfRejected(result);
        assertSessionUntouched(login.cookie());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REFRESH, LOGOUT})
    @DisplayName("[AC-39] Origin non autorisée : 403 ProblemDetail (AuthCsrfFilter avant CORS), session intacte")
    void ac39_unknownOriginIsRejected(String route) {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = postWithHeaders(route, login.cookie(), EVIL_ORIGIN, "XMLHttpRequest");

        // Then
        assertCsrfRejected(result);
        assertSessionUntouched(login.cookie());
    }

    private static void assertCsrfRejected(MvcTestResult result) {
        assertProblem(result, 403, "Forbidden", "Cross-site request rejected.");
        assertNoSetCookie(result);
    }

    /** Jeton non consommé et famille non révoquée. */
    private void assertSessionUntouched(String refreshCookie) {
        assertThat(usedAt(refreshCookie)).isNull();
        assertThat(revokedAt(familyOf(refreshCookie))).isNull();
    }
}
