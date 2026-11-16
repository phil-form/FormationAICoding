package be.bstorm.formation.framework.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SecurityPropertiesTest {

    private static final String STRICT_CSP = "default-src 'none'; frame-ancestors 'none'";

    @Test
    void strictConfigurationWithHttpsOriginsIsAccepted() {
        assertThat(strict(true, List.of("https://app.example.be"), List.of(), STRICT_CSP).strictViolations())
                .isEmpty();
    }

    @Test
    void nonStrictConfigurationIsNeverChecked() {
        SecurityProperties props = new SecurityProperties(false, false, 8443, null,
                new SecurityProperties.Hsts(false, Duration.ZERO, false, false), "x",
                cors(List.of("*"), List.of("http://localhost:*")));
        assertThat(props.strictViolations()).isEmpty();
    }

    @Test
    void strictModeRejectsInsecureSettings() {
        SecurityProperties props = new SecurityProperties(true, false, 443, null,
                new SecurityProperties.Hsts(true, Duration.ofDays(30), true, true),
                "default-src 'self'; script-src 'unsafe-inline'",
                cors(List.of("http://app.example.be", "https://*.example.be"), List.of("https://*.example.be")));

        assertThat(props.strictViolations()).anySatisfy(e -> assertThat(e).contains("require-https"))
                .anySatisfy(e -> assertThat(e).contains("max-age"))
                .anySatisfy(e -> assertThat(e).contains("non HTTPS"))
                .anySatisfy(e -> assertThat(e).contains("joker"))
                .anySatisfy(e -> assertThat(e).contains("allowed-origin-patterns"))
                .anySatisfy(e -> assertThat(e).contains("unsafe-"));
        assertThatIllegalStateException().isThrownBy(props::assertStrict);
    }

    @Test
    void strictModeRequiresAtLeastOneOrigin() {
        assertThat(strict(true, List.of(), List.of(), STRICT_CSP).strictViolations())
                .anySatisfy(e -> assertThat(e).contains("CORS_ALLOWED_ORIGINS"));
    }

    @Test
    void strictModeRequiresHsts() {
        SecurityProperties props = new SecurityProperties(true, true, 443, null,
                new SecurityProperties.Hsts(false, Duration.ofDays(730), true, true), STRICT_CSP,
                cors(List.of("https://app.example.be"), List.of()));
        assertThat(props.strictViolations()).anySatisfy(e -> assertThat(e).contains("hsts.enabled"));
    }

    private static SecurityProperties strict(boolean strict, List<String> origins, List<String> patterns, String csp) {
        return new SecurityProperties(strict, true, 443, null,
                new SecurityProperties.Hsts(true, Duration.ofDays(730), true, true), csp, cors(origins, patterns));
    }

    private static SecurityProperties.Cors cors(List<String> origins, List<String> patterns) {
        return new SecurityProperties.Cors(origins, patterns, List.of("GET"), List.of("Authorization"),
                List.of("Location"), false, Duration.ofHours(1));
    }
}
