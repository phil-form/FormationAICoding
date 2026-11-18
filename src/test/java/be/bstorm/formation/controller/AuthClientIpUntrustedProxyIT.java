package be.bstorm.formation.controller;

import be.bstorm.formation.support.AuthTomcatItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 001 — AC-67 : {@code X-Forwarded-For} ignoré quand la source n'est pas un relais de confiance (SEC-13). */
@Tag("feature-001")
class AuthClientIpUntrustedProxyIT extends AuthTomcatItSupport {

    @DynamicPropertySource
    static void untrustedClient(DynamicPropertyRegistry registry) {
        registry.add("server.tomcat.remoteip.internal-proxies", () -> "10\\.255\\.255\\.255");
    }

    @Test
    @DisplayName("[AC-67] Source non fiable : X-Forwarded-For ignoré, tous les échecs comptés sur 127.0.0.1")
    void ac67_forwardedHeaderFromUntrustedSourceIsIgnored(CapturedOutput output) throws Exception {
        // Given
        createAlice();
        for (int i = 1; i <= 20; i++) {
            assertThat(login("unknown-%02d".formatted(i), WRONG_PASSWORD, "198.51.100." + (100 + i))).isEqualTo(401);
        }

        // When
        int status = login("alice", PASSWORD, "198.51.100.200");

        // Then
        assertThat(status).isEqualTo(429);
        assertThat(loginFailureLines(output)).isNotEmpty()
                .allMatch(line -> line.contains(" ip=127.0.0.1"));
    }
}
