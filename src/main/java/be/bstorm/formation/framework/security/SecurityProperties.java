package be.bstorm.formation.framework.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration de sécurité HTTP ({@code app.security.*}).
 * <p>
 * En mode {@code strict} (profil prod), la cohérence est vérifiée au démarrage par {@link #assertStrict()} :
 * HTTPS obligatoire, HSTS actif, CORS limité à des origines HTTPS explicites. L'application refuse de démarrer
 * sinon.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        boolean strict,
        boolean requireHttps,
        @Min(1) @NotNull Integer httpsPort,
        String redirectHost,
        @Valid @NotNull Hsts hsts,
        @NotBlank String contentSecurityPolicy,
        @Valid @NotNull Cors cors
) {

    public record Hsts(
            boolean enabled,
            @NotNull Duration maxAge,
            boolean includeSubDomains,
            boolean preload
    ) {}

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedOriginPatterns,
            @NotNull List<String> allowedMethods,
            @NotNull List<String> allowedHeaders,
            @NotNull List<String> exposedHeaders,
            boolean allowCredentials,
            @NotNull Duration maxAge
    ) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
            allowedOriginPatterns = allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
        }
    }

    /** Durée minimale HSTS exigée en mode strict (1 an, recommandation OWASP / preload list). */
    static final Duration STRICT_MIN_HSTS = Duration.ofDays(365);

    /**
     * Vérifie les invariants du mode strict. Retourne la liste des violations (vide si conforme).
     */
    public List<String> strictViolations() {
        List<String> errors = new ArrayList<>();
        if (!strict) {
            return errors;
        }
        if (!requireHttps) {
            errors.add("app.security.require-https doit être true");
        }
        if (!hsts.enabled()) {
            errors.add("app.security.hsts.enabled doit être true");
        } else if (hsts.maxAge().compareTo(STRICT_MIN_HSTS) < 0) {
            errors.add("app.security.hsts.max-age doit être >= 365 jours");
        }
        if (cors.allowedOrigins().isEmpty()) {
            errors.add("app.security.cors.allowed-origins doit lister au moins une origine (CORS_ALLOWED_ORIGINS)");
        }
        for (String origin : cors.allowedOrigins()) {
            if (origin.contains("*")) {
                errors.add("origine CORS avec joker interdite : " + origin);
            } else if (!origin.startsWith("https://")) {
                errors.add("origine CORS non HTTPS interdite : " + origin);
            }
        }
        if (!cors.allowedOriginPatterns().isEmpty()) {
            errors.add("app.security.cors.allowed-origin-patterns interdit en mode strict");
        }
        if (cors.allowedHeaders().contains("*")) {
            errors.add("app.security.cors.allowed-headers ne peut pas contenir '*' en mode strict");
        }
        if (contentSecurityPolicy.contains("unsafe-")) {
            errors.add("la CSP ne peut pas contenir de directive 'unsafe-*' en mode strict");
        }
        return errors;
    }

    public void assertStrict() {
        List<String> errors = strictViolations();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Configuration de sécurité invalide (mode strict) :\n - "
                    + String.join("\n - ", errors));
        }
    }
}
