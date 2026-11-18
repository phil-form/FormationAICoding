package be.bstorm.formation.framework.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Cookie du jeton de rafraîchissement (RG-08, ADR 0002 D2) : {@code __Secure-refresh}, {@code HttpOnly; Secure;
 * SameSite=Strict; Path=/api/v1/auth}, sans {@code Domain}.
 */
public final class AuthCookies {

    public static final String REFRESH_COOKIE = "__Secure-refresh";
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private AuthCookies() {
    }

    /** __Secure-refresh=&lt;jeton&gt;; Path=/api/v1/auth; Max-Age=&lt;ttl en secondes, arrondi inférieur&gt;; Secure; HttpOnly; SameSite=Strict */
    public static ResponseCookie issued(String refreshToken, Duration ttl) {
        long seconds = ttl == null ? 0 : Math.max(0, ttl.getSeconds());
        return base(refreshToken).maxAge(Duration.ofSeconds(seconds)).build();
    }

    /** __Secure-refresh=; Path=/api/v1/auth; Max-Age=0; Secure; HttpOnly; SameSite=Strict */
    public static ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .path(REFRESH_COOKIE_PATH)
                .secure(true)
                .httpOnly(true)
                .sameSite("Strict");
    }
}
