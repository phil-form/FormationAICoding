package be.bstorm.formation.framework.config;

import be.bstorm.formation.framework.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS pour le front Angular, piloté par {@code app.security.cors.*}.
 * En prod : origines HTTPS explicites uniquement (vérifié par {@link SecurityProperties#assertStrict()}).
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        SecurityProperties.Cors cors = properties.cors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedOriginPatterns(cors.allowedOriginPatterns());
        config.setAllowedMethods(cors.allowedMethods());
        config.setAllowedHeaders(cors.allowedHeaders());
        config.setExposedHeaders(cors.exposedHeaders());
        config.setAllowCredentials(cors.allowCredentials());
        config.setMaxAge(cors.maxAge());

        // ADR 0002, D3 : seules les routes d'authentification utilisent le cookie de refresh → credentials autorisés
        // UNIQUEMENT sur /api/v1/auth/**, avec l'en-tête personnalisé anti-CSRF (impose un preflight).
        CorsConfiguration auth = new CorsConfiguration(config);
        auth.setAllowedHeaders(withHeader(cors.allowedHeaders(), CSRF_HEADER));
        auth.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(AUTH_PATHS, auth);
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Routes d'authentification (cookie {@code __Secure-refresh}, {@code Path=/api/v1/auth}). */
    public static final String AUTH_PATHS = "/api/v1/auth/**";

    /** En-tête personnalisé obligatoire sur les routes utilisant le cookie (ADR 0002, D3). */
    public static final String CSRF_HEADER = "X-Requested-With";

    private static List<String> withHeader(List<String> headers, String header) {
        if (headers.contains("*") || headers.stream().anyMatch(header::equalsIgnoreCase)) {
            return headers;
        }
        List<String> result = new ArrayList<>(headers);
        result.add(header);
        return result;
    }
}
