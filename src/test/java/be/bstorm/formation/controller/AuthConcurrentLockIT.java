package be.bstorm.formation.controller;

import be.bstorm.formation.support.AuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 001 — AC-49 : échecs de login concurrents sur un même compte (RG-23, RG-04, SEC-10). */
@Tag("feature-001")
@TestPropertySource(properties = {
        "app.auth.password.max-concurrent-hashes=10",
        "app.auth.password.memory-kib=19456",
        "app.auth.password.iterations=2"})
class AuthConcurrentLockIT extends AuthItSupport {

    @Test
    @DisplayName("[AC-49] 10 mauvais mots de passe simultanés : 10 × 401, compte verrouillé, un seul ACCOUNT_LOCKED")
    void ac49_concurrentFailuresLockAccountExactlyOnce(CapturedOutput output) throws Exception {
        // Given
        UUID alice = createAlice();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();

        // When
        try {
            List<Future<MvcTestResult>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String clientIp = "203.0.113." + (101 + i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return login("alice", WRONG_PASSWORD, clientIp);
                }));
            }
            start.countDown();
            for (Future<MvcTestResult> future : futures) {
                statuses.add(future.get(120, TimeUnit.SECONDS).getResponse().getStatus());
            }
        } finally {
            pool.shutdownNow();
        }

        // Then
        assertThat(statuses).hasSize(10).containsOnly(401);
        assertThat(failedLoginCount(alice)).isZero();
        assertThat(lockedUntil(alice)).isEqualTo(T0.plus(Duration.ofMinutes(15)));
        assertThat(eventLines(output, "ACCOUNT_LOCKED")).hasSize(1);
    }
}
