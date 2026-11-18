package be.bstorm.formation.controller;

import be.bstorm.formation.dto.request.LoginRequest;
import be.bstorm.formation.dto.response.CurrentUserResponse;
import be.bstorm.formation.dto.response.TokenResponse;
import be.bstorm.formation.framework.security.AuthCookies;
import be.bstorm.formation.service.AuthService;
import be.bstorm.formation.service.AuthService.IssuedTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentification (feature 001, ADR 0002) : jeton d'accès dans le corps, jeton de rafraîchissement uniquement dans le
 * cookie {@code __Secure-refresh}. L'IP cliente passée au service est {@code getRemoteAddr()} (résolue par Tomcat).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String REFRESH_COOKIE = AuthCookies.REFRESH_COOKIE;
    public static final String REFRESH_COOKIE_PATH = AuthCookies.REFRESH_COOKIE_PATH;

    private final AuthService authService;

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return withRefreshCookie(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        return withRefreshCookie(authService.refresh(refreshToken, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        authService.logout(refreshToken, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.cleared().toString())
                .build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return authService.currentUser();
    }

    private static ResponseEntity<TokenResponse> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.issued(tokens.refreshToken(), tokens.refreshTtl()).toString())
                .body(tokens.body());
    }
}
