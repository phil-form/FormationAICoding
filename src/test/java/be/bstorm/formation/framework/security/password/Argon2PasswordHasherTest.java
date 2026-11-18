package be.bstorm.formation.framework.security.password;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.crypto.InMemoryPasswordPepper;
import be.bstorm.formation.framework.crypto.PasswordPepper;
import be.bstorm.formation.framework.exception.RetryLaterException;
import be.bstorm.formation.framework.security.AuthProperties;
import be.bstorm.formation.framework.security.password.PasswordHasher.Verification;
import be.bstorm.formation.support.TestAuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Feature 001 — {@link Argon2PasswordHasher} : RG-14 (format, poivre, re-hachage), RG-21 (sémaphore), AC-18, AC-45. */
@Tag("feature-001")
class Argon2PasswordHasherTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    /** Chaîne Argon2id valide (sans poivre ni préfixe) pour construire des hachages mal formés. */
    private static final String ARGON2_STRING = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2).encode("anything");

    private static final AuthProperties DEFAULTS = TestAuthProperties.defaults();
    private static final AuthProperties FAST = TestAuthProperties.withPassword(DEFAULTS, 19_456, 2, 4, Duration.ofSeconds(2));

    private final InMemoryPasswordPepper pepper = new InMemoryPasswordPepper();
    private ExecutorService pool;
    private CountDownLatch release;

    @AfterEach
    void releaseBlockedThreads() {
        if (release != null) {
            release.countDown();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("[RG-14] hash = p<version>$ + Argon2id aux paramètres par défaut, sans le mot de passe, salé")
    void rg14_hashFormatWithDefaultParameters() {
        // Given
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(pepper, DEFAULTS);

        // When
        String first = hasher.hash(PASSWORD);
        String second = hasher.hash(PASSWORD);

        // Then
        assertThat(first).matches("^p1\\$\\$argon2id\\$v=19\\$m=65536,t=3,p=1\\$.+\\$.+$").doesNotContain(PASSWORD);
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("[AC-18] Bon mot de passe : MATCH")
    void ac18_correctPasswordMatches() {
        // Given
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(pepper, FAST);
        String stored = hasher.hash(PASSWORD);

        // When / Then
        assertThat(hasher.verify(PASSWORD, stored)).isEqualTo(Verification.MATCH);
    }

    @Test
    @DisplayName("[AC-18] Mauvais mot de passe : MISMATCH")
    void ac18_wrongPasswordMismatches() {
        // Given
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(pepper, FAST);
        String stored = hasher.hash(PASSWORD);

        // When / Then
        assertThat(hasher.verify("Wrong-Horse-Battery-0", stored)).isEqualTo(Verification.MISMATCH);
    }

    static Stream<String> malformedHashes() {
        return Stream.of("abc", "p1$", "px$" + ARGON2_STRING, "p0$" + ARGON2_STRING, "", null);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("malformedHashes")
    @DisplayName("[AC-18] Hachage mal formé : MISMATCH sans exception ni appel au poivre")
    void ac18_malformedHashMismatchesWithoutCallingPepper(String malformed) {
        // Given
        PasswordPepper observedPepper = spy(new InMemoryPasswordPepper());
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(observedPepper, FAST);

        // When
        Verification result = hasher.verify(PASSWORD, malformed);

        // Then
        assertThat(result).isEqualTo(Verification.MISMATCH);
        verifyNoInteractions(observedPepper);
    }

    @Test
    @DisplayName("[AC-18] Version de poivre inconnue (p99$) sur un hachage bien formé : CryptoException propagée")
    void ac18_unknownPepperVersionPropagatesCryptoException() {
        // Given
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(pepper, FAST);
        String valid = hasher.hash(PASSWORD);
        String unknownVersion = "p99$" + valid.substring(valid.indexOf('$') + 1);

        // When / Then
        assertThatThrownBy(() -> hasher.verify(PASSWORD, unknownVersion)).isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("[RG-14] Version de poivre stockée inférieure à la dernière : MATCH_REHASH_NEEDED, nouveau hachage en p2$")
    void rg14_olderPepperVersionNeedsRehash() {
        // Given
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(pepper, FAST);
        String stored = hasher.hash(PASSWORD);
        pepper.rotate();

        // When
        Verification result = hasher.verify(PASSWORD, stored);

        // Then
        assertThat(result).isEqualTo(Verification.MATCH_REHASH_NEEDED);
        assertThat(hasher.hash(PASSWORD)).startsWith("p2$");
    }

    @Test
    @DisplayName("[RG-14] Paramètres Argon2 stockés inférieurs aux courants : MATCH_REHASH_NEEDED")
    void rg14_weakerArgon2ParametersNeedRehash() {
        // Given
        String weak = new Argon2PasswordHasher(pepper, FAST).hash(PASSWORD);
        Argon2PasswordHasher current = new Argon2PasswordHasher(pepper, DEFAULTS);

        // When / Then
        assertThat(current.verify(PASSWORD, weak)).isEqualTo(Verification.MATCH_REHASH_NEEDED);
    }

    @Test
    @DisplayName("[AC-45] Sémaphore plein : hash, verify (hachage bien formé) et dummyVerify lèvent RetryLaterException (1 s)")
    void ac45_saturatedSemaphoreRejectsEveryHashingCall() throws Exception {
        // Given
        BlockedHasher blocked = blockOneHashingSlot();

        // When / Then
        assertThatThrownBy(() -> blocked.hasher().hash(PASSWORD))
                .isInstanceOfSatisfying(RetryLaterException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(1)));
        assertThatThrownBy(() -> blocked.hasher().verify(PASSWORD, blocked.wellFormedHash()))
                .isInstanceOfSatisfying(RetryLaterException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(1)));
        assertThatThrownBy(() -> blocked.hasher().dummyVerify(PASSWORD))
                .isInstanceOfSatisfying(RetryLaterException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("[AC-45] Une fois le thread bloquant libéré, un nouvel appel réussit")
    void ac45_hashingResumesOnceSlotIsReleased() throws Exception {
        // Given
        BlockedHasher blocked = blockOneHashingSlot();

        // When
        release.countDown();

        // Then
        assertThat(blocked.holder().get(10, TimeUnit.SECONDS)).startsWith("p1$");
        assertThat(blocked.hasher().verify(PASSWORD, blocked.wellFormedHash())).isEqualTo(Verification.MATCH);
    }

    @Test
    @DisplayName("[RG-21] Le contrôle de format précède le sémaphore : hachage mal formé = MISMATCH même saturé")
    void rg21_malformedHashIsCheckedBeforeSemaphore() throws Exception {
        // Given
        BlockedHasher blocked = blockOneHashingSlot();

        // When
        Verification result = blocked.hasher().verify(PASSWORD, "abc");

        // Then
        assertThat(result).isEqualTo(Verification.MISMATCH);
    }

    private record BlockedHasher(Argon2PasswordHasher hasher, String wellFormedHash, Future<String> holder) {
    }

    /**
     * max-concurrent-hashes = 1, hash-acquire-timeout = 100 ms ; un hachage bien formé est calculé d'avance, puis un
     * thread A occupe l'unique place, bloqué dans le poivre.
     */
    private BlockedHasher blockOneHashingSlot() throws InterruptedException {
        PasswordPepper blockingPepper = mock(PasswordPepper.class);
        AtomicBoolean blockNext = new AtomicBoolean(false);
        CountDownLatch entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        CountDownLatch releaseLatch = release;
        when(blockingPepper.pepper(anyString())).thenAnswer(invocation -> {
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown();
                releaseLatch.await(10, TimeUnit.SECONDS);
            }
            return pepper.pepper(invocation.<String>getArgument(0));
        });
        when(blockingPepper.pepper(anyString(), anyInt()))
                .thenAnswer(invocation -> pepper.pepper(invocation.<String>getArgument(0), invocation.<Integer>getArgument(1)));
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(blockingPepper,
                TestAuthProperties.withPassword(DEFAULTS, 19_456, 2, 1, Duration.ofMillis(100)));
        String wellFormed = hasher.hash(PASSWORD);
        blockNext.set(true);
        pool = Executors.newSingleThreadExecutor();
        Future<String> holder = pool.submit(() -> hasher.hash("Thread-A-Password-1"));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(wellFormed).startsWith("p1$$argon2id$");
        return new BlockedHasher(hasher, wellFormed, holder);
    }
}
