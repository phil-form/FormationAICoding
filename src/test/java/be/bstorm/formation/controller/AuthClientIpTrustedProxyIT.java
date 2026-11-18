package be.bstorm.formation.controller;

import be.bstorm.formation.support.AuthTomcatItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 001 — AC-66 / AC-SEC-14 : le client de test (127.0.0.1) est un relais de confiance ; l'IP cliente est celle
 * résolue par {@code RemoteIpValve} depuis {@code X-Forwarded-For} au format du WAF ({@code <client>, <client>}).
 */
@Tag("feature-001")
class AuthClientIpTrustedProxyIT extends AuthTomcatItSupport {

    @DynamicPropertySource
    static void trustedProxies(DynamicPropertyRegistry registry) {
        registry.add("server.tomcat.remoteip.internal-proxies", () -> "127\\.0\\.0\\.1|0:0:0:0:0:0:0:1");
    }

    @Test
    @DisplayName("[AC-66] IP résolue depuis un relais de confiance : blocage par client réel, pas par relais")
    void ac66_rateLimitUsesClientIpResolvedFromTrustedProxy(CapturedOutput output) throws Exception {
        // Given
        createAlice();
        for (int i = 1; i <= 20; i++) {
            assertThat(login("unknown-%02d".formatted(i), WRONG_PASSWORD, "198.51.100.7, 198.51.100.7")).isEqualTo(401);
        }

        // When
        int otherClient = login("alice", PASSWORD, "198.51.100.8, 198.51.100.8");
        int blockedClient = login("alice", PASSWORD, "198.51.100.7, 198.51.100.7");

        // Then
        assertThat(otherClient).isEqualTo(200);
        assertThat(blockedClient).isEqualTo(429);
        assertThat(loginFailureLines(output)).anyMatch(line -> line.contains(" ip=198.51.100.7 "));
        assertThat(loginFailureLines(output)).noneMatch(line -> line.contains(" ip=127.0.0.1"));
    }

    @Test
    @DisplayName("[AC-SEC-14] L'adresse journalisée est l'IP cliente résolue, jamais celle du relais")
    void acsec14_loggedIpIsTheResolvedClientIp(CapturedOutput output) throws Exception {
        // When
        int status = login("unknown-probe", WRONG_PASSWORD, "198.51.100.9, 198.51.100.9");

        // Then
        assertThat(status).isEqualTo(401);
        assertThat(loginFailureLines(output)).anyMatch(line -> line.contains(" ip=198.51.100.9 "));
        assertThat(loginFailureLines(output)).noneMatch(line -> line.contains(" ip=127.0.0.1"));
    }
}
