package be.bstorm.formation.framework.config;

import be.bstorm.formation.framework.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
