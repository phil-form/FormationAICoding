package be.bstorm.formation.controller;

import be.bstorm.formation.framework.crypto.PasswordPepper;
import be.bstorm.formation.support.AuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** Feature 001 — AC-45 (partie HTTP) : hachage saturé → 503 + Retry-After: 1 (RG-21). */
@Tag("feature-001")
@TestPropertySource(properties = {
        "app.auth.password.max-concurrent-hashes=1",
        "app.auth.password.hash-acquire-timeout=100ms",
        "app.auth.password.memory-kib=19456",
        "app.auth.password.iterations=2"})
@MockitoSpyBean(types = PasswordPepper.class)
class AuthHashSaturationIT extends AuthItSupport {

    @Test
    @DisplayName("[AC-45] Place de hachage indisponible : 503 Service Unavailable, Retry-After: 1")
    void ac45_saturatedHasherAnswers503WithRetryAfter() throws Exception {
        // Given : alice créée avant que le poivre ne bloque
        createAlice();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(pepper).pepper(anyString());
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<MvcTestResult> holder = pool.submit(() -> login("alice", PASSWORD, "198.51.100.21"));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            // When
            MvcTestResult busy = login("nobody-busy", WRONG_PASSWORD, "198.51.100.22");

            // Then
            assertProblem(busy, 503, "Service Unavailable", "The service is busy. Retry later.");
            assertThat(busy).hasHeader(HttpHeaders.RETRY_AFTER, "1");
            release.countDown();
            assertThat(holder.get(30, TimeUnit.SECONDS)).hasStatusOk();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
