package be.bstorm.formation.support;

import be.bstorm.formation.framework.security.AuthProperties;

import java.time.Duration;

/** {@link AuthProperties} pour les tests unitaires : valeurs par défaut du § 5.5 de l'analyse 001, surcharges ciblées. */
public final class TestAuthProperties {

    private TestAuthProperties() {
    }

    /** Valeurs par défaut de {@code application.yml} (§ 5.5). */
    public static AuthProperties defaults() {
        return new AuthProperties(
                new AuthProperties.Jwt("formation-api", "formation-front", Duration.ofMinutes(5), Duration.ofSeconds(60)),
                new AuthProperties.Refresh(Duration.ofHours(8), Duration.ofHours(24), Duration.ofDays(7), "0 15 3 * * *"),
                new AuthProperties.Login(5, Duration.ofMinutes(15), 20, Duration.ofMinutes(5), 10_000),
                new AuthProperties.Password(16, 32, 1, 65_536, 3, 4, Duration.ofSeconds(2)));
    }

    /** Même configuration, paramètres Argon2 / sémaphore remplacés. */
    public static AuthProperties withPassword(AuthProperties base, int memoryKib, int iterations,
                                              int maxConcurrentHashes, Duration hashAcquireTimeout) {
        AuthProperties.Password p = base.password();
        return new AuthProperties(base.jwt(), base.refresh(), base.login(),
                new AuthProperties.Password(p.saltLength(), p.hashLength(), p.parallelism(), memoryKib, iterations,
                        maxConcurrentHashes, hashAcquireTimeout));
    }

    /** Même configuration, limiteur IP remplacé. */
    public static AuthProperties withIpLimits(AuthProperties base, int ipMaxFailures, int ipMaxTracked) {
        AuthProperties.Login l = base.login();
        return new AuthProperties(base.jwt(), base.refresh(),
                new AuthProperties.Login(l.maxFailedAttempts(), l.lockDuration(), ipMaxFailures, l.ipWindow(), ipMaxTracked),
                base.password());
    }
}
