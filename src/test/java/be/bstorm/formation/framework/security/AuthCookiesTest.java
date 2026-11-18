package be.bstorm.formation.framework.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 001 — {@link AuthCookies} (RG-08, § 5.1). */
@Tag("feature-001")
class AuthCookiesTest {

    @Test
    @DisplayName("[RG-08] Cookie émis : __Secure-refresh, Path=/api/v1/auth, Max-Age arrondi inférieur, Secure, HttpOnly, Strict, sans Domain")
    void rg08_issuedCookieAttributes() {
        // When
        ResponseCookie cookie = AuthCookies.issued("refresh-value", Duration.ofSeconds(10_800).plusMillis(999));

        // Then
        assertThat(cookie.getName()).isEqualTo("__Secure-refresh").isEqualTo(AuthCookies.REFRESH_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("refresh-value");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth").isEqualTo(AuthCookies.REFRESH_COOKIE_PATH);
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(10_800));
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    @DisplayName("[RG-08] Cookie effacé : valeur vide, Max-Age=0, mêmes attributs de sécurité")
    void rg08_clearedCookieAttributes() {
        // When
        ResponseCookie cookie = AuthCookies.cleared();

        // Then
        assertThat(cookie.getName()).isEqualTo("__Secure-refresh");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getDomain()).isNull();
    }
}
