package be.bstorm.formation.framework.security.jwt;

import be.bstorm.formation.framework.security.AuthCsrfFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Set;

/**
 * Lecture du jeton d'accès (RG-07, RG-20) : uniquement l'en-tête {@code Authorization: Bearer} (jamais un paramètre
 * d'URL, le corps ni un cookie). Ignoré ({@code null}) si et seulement si la méthode est {@code POST} et le chemin est
 * exactement {@code /api/v1/auth/login}, {@code /refresh} ou {@code /logout}.
 */
public class AuthAwareBearerTokenResolver implements BearerTokenResolver {

    static final Set<String> IGNORED_POST_PATHS =
            Set.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");

    private final DefaultBearerTokenResolver delegate;

    public AuthAwareBearerTokenResolver() {
        this.delegate = new DefaultBearerTokenResolver();
        this.delegate.setAllowUriQueryParameter(false);
        this.delegate.setAllowFormEncodedBodyParameter(false);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if ("POST".equals(request.getMethod())
                && IGNORED_POST_PATHS.contains(AuthCsrfFilter.pathWithinApplication(request))) {
            return null;
        }
        return delegate.resolve(request);
    }
}
