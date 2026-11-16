package be.bstorm.formation.framework.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Set;

/**
 * Force HTTPS quand {@code app.security.require-https=true}.
 * <ul>
 *   <li>{@code GET}/{@code HEAD} en clair : redirection permanente (301) vers HTTPS ;</li>
 *   <li>toute autre méthode en clair : refus (403) — rediriger un POST/PUT ne protège pas un corps
 *       déjà transmis en clair, le client doit être corrigé.</li>
 * </ul>
 * Le port de management (actuator interne, non publié) est exclu pour les healthchecks du conteneur.
 * Derrière un reverse proxy TLS, {@code server.forward-headers-strategy=native} fait que
 * {@link HttpServletRequest#isSecure()} reflète {@code X-Forwarded-Proto}.
 */
@RequiredArgsConstructor
public class HttpsEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> REDIRECTABLE = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name());

    private final SecurityProperties properties;
    private final Integer managementPort;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.requireHttps()
                || request.isSecure()
                || (managementPort != null && request.getLocalPort() == managementPort);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (REDIRECTABLE.contains(request.getMethod())) {
            response.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
            response.setHeader("Location", httpsUrl(request));
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"HTTPS Required","status":403,\
                "detail":"This API only accepts HTTPS requests."}""");
    }

    private String httpsUrl(HttpServletRequest request) {
        String host = properties.redirectHost() != null && !properties.redirectHost().isBlank()
                ? properties.redirectHost()
                : request.getServerName();
        int port = properties.httpsPort();
        return UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(host)
                .port(port == 443 ? -1 : port)
                .path(request.getRequestURI())
                .query(request.getQueryString())
                .build(true)
                .toUriString();
    }
}
