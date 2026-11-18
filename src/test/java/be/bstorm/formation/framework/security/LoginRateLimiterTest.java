package be.bstorm.formation.framework.security;

import be.bstorm.formation.support.MutableClock;
import be.bstorm.formation.support.TestAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 001 — {@link LoginRateLimiter} (RG-05) : clés IPv4 / IPv6 /64, fenêtre, table bornée. */
@Tag("feature-001")
class LoginRateLimiterTest {

    private static final Instant T = Instant.parse("2026-01-15T08:00:00Z");

    private final MutableClock clock = new MutableClock(T);

    private void fail(LoginRateLimiter limiter, String clientIp, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(clientIp);
        }
    }

    @Test
    @DisplayName("[AC-53] IPv6 : même clé pour tout le /64 ; IPv4-mapped traitée comme l'IPv4")
    void ac53_ipv6IsKeyedBySlash64AndMappedIpv4AsIpv4() {
        // Given
        LoginRateLimiter limiter = new LoginRateLimiter(TestAuthProperties.defaults(), clock);

        // When
        fail(limiter, "2001:db8::1", 20);
        fail(limiter, "::ffff:203.0.113.20", 20);

        // Then
        assertThat(limiter.blockedFor("2001:db8::ffff")).isPresent();
        assertThat(limiter.blockedFor("2001:db8:0:1::1")).isEmpty();
        assertThat(limiter.blockedFor("203.0.113.20")).isPresent();
    }

    @Test
    @DisplayName("[AC-54] Table pleine : la clé au plus ancien windowStart est évincée ; reset() vide tout")
    void ac54_fullTableEvictsOldestKeyAndResetClearsAll() {
        // Given
        LoginRateLimiter limiter = new LoginRateLimiter(
                TestAuthProperties.withIpLimits(TestAuthProperties.defaults(), 1, 2), clock);

        // When
        limiter.recordFailure("198.51.100.1");
        clock.set(T.plusSeconds(1));
        limiter.recordFailure("198.51.100.2");
        clock.set(T.plusSeconds(2));
        limiter.recordFailure("198.51.100.3");

        // Then
        assertThat(limiter.blockedFor("198.51.100.1")).isEmpty();
        assertThat(limiter.blockedFor("198.51.100.2")).isPresent();
        assertThat(limiter.blockedFor("198.51.100.3")).isPresent();

        // When
        limiter.reset();

        // Then
        assertThat(limiter.blockedFor("198.51.100.1")).isEmpty();
        assertThat(limiter.blockedFor("198.51.100.2")).isEmpty();
        assertThat(limiter.blockedFor("198.51.100.3")).isEmpty();
    }

    @Test
    @DisplayName("[RG-05] 19 échecs ne bloquent pas, le 20e bloque")
    void rg05_blocksAtTwentiethFailure() {
        // Given
        LoginRateLimiter limiter = new LoginRateLimiter(TestAuthProperties.defaults(), clock);

        // When
        fail(limiter, "203.0.113.5", 19);

        // Then
        assertThat(limiter.blockedFor("203.0.113.5")).isEmpty();

        // When
        limiter.recordFailure("203.0.113.5");

        // Then
        assertThat(limiter.blockedFor("203.0.113.5")).isPresent();
    }

    @Test
    @DisplayName("[RG-05] Durée restante jusqu'à windowStart + 5 min ; débloqué exactement à la fin de la fenêtre")
    void rg05_remainingDurationUntilWindowEnd() {
        // Given
        LoginRateLimiter limiter = new LoginRateLimiter(TestAuthProperties.defaults(), clock);
        fail(limiter, "203.0.113.6", 20);

        // When / Then
        clock.set(T.plusSeconds(60));
        assertThat(limiter.blockedFor("203.0.113.6")).contains(Duration.ofSeconds(240));
        clock.set(T.plusSeconds(299));
        assertThat(limiter.blockedFor("203.0.113.6")).contains(Duration.ofSeconds(1));
        clock.set(T.plusSeconds(300));
        assertThat(limiter.blockedFor("203.0.113.6")).isEmpty();
    }

    @Test
    @DisplayName("[RG-05] Un échec après la fin de la fenêtre ouvre une nouvelle fenêtre (count = 1)")
    void rg05_failureAfterWindowEndOpensNewWindow() {
        // Given
        LoginRateLimiter limiter = new LoginRateLimiter(TestAuthProperties.defaults(), clock);
        fail(limiter, "203.0.113.7", 20);

        // When
        clock.set(T.plus(Duration.ofMinutes(5)));
        limiter.recordFailure("203.0.113.7");

        // Then
        assertThat(limiter.blockedFor("203.0.113.7")).isEmpty();
    }

    @Test
    @DisplayName("[RG-05] Adresse non littérale : la chaîne brute sert de clé")
    void rg05_nonLiteralAddressIsUsedAsRawKey() {
        // Given
        LoginRateLimiter limiter = new LoginRateLimiter(TestAuthProperties.defaults(), clock);

        // When
        fail(limiter, "not-an-ip-literal", 20);

        // Then
        assertThat(limiter.blockedFor("not-an-ip-literal")).isPresent();
        assertThat(limiter.blockedFor("203.0.113.8")).isEmpty();
    }
}
