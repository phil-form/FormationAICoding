package be.bstorm.formation.framework.security;

import be.bstorm.formation.framework.config.CorsConfig;
import be.bstorm.formation.framework.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Anti-CSRF des routes à cookie (RG-13, ADR 0002 D3) : {@code POST /api/v1/auth/refresh} et
 * {@code POST /api/v1/auth/logout} exigent un en-tête {@code Origin} accepté par la configuration CORS résolue pour la
 * requête ({@code Origin: null} jamais accepté) et un en-tête {@link CorsConfig#CSRF_HEADER} non vide.
 * Sinon : 403 {@code ProblemDetail} (« Cross-site request rejected. »), la chaîne s'arrête.
 * <p>
 * Placé avant le filtre CORS. Pas un {@code @Component} (sinon enregistré aussi comme filtre servlet) : instancié dans
 * {@code SecurityConfig}.
 */
public class AuthCsrfFilter extends OncePerRequestFilter {

    static final String REFRESH_PATH = "/api/v1/auth/refresh";
    static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final CorsConfigurationSource corsConfigurationSource;
    private final HandlerExceptionResolver resolver;

    public AuthCsrfFilter(CorsConfigurationSource corsConfigurationSource, HandlerExceptionResolver resolver) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = pathWithinApplication(request);
        return !(REFRESH_PATH.equals(path) || LOGOUT_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (originAccepted(request) && csrfHeaderPresent(request)) {
            chain.doFilter(request, response);
            return;
        }
        SecurityEvents.warn("CSRF_REJECTED", null, request.getRemoteAddr(), null, null, null);
        resolver.resolveException(request, response, null,
                new BusinessException(HttpStatus.FORBIDDEN, "Forbidden", "Cross-site request rejected."));
    }

    private boolean originAccepted(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin.trim())) {
            return false;
        }
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        return config != null && config.checkOrigin(origin) != null;
    }

    private static boolean csrfHeaderPresent(HttpServletRequest request) {
        String value = request.getHeader(CorsConfig.CSRF_HEADER);
        return value != null && !value.trim().isEmpty();
    }

    /** Chemin sans le context path (RG-13, RG-20 : chemins exacts). */
    public static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
