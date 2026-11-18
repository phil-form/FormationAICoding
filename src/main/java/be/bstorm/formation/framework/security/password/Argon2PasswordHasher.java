package be.bstorm.formation.framework.security.password;

import be.bstorm.formation.framework.crypto.PasswordPepper;
import be.bstorm.formation.framework.exception.RetryLaterException;
import be.bstorm.formation.framework.security.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implémentation unique de {@link PasswordHasher} (RG-14, RG-21, RG-03).
 * <ul>
 *   <li>Argon2id, paramètres {@code app.auth.password.*} ;</li>
 *   <li>nombre de calculs simultanés limité par un sémaphore, pris avant le poivre et rendu après Argon2 ; place non
 *       obtenue dans le délai → {@link RetryLaterException} 503, {@code Retry-After: 1} ;</li>
 *   <li>hachage factice calculé dans le constructeur, sans poivre (aucun appel à OpenBao au démarrage).</li>
 * </ul>
 * Ne journalise jamais le mot de passe, le poivre ni le hachage.
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

    /** Hachage stocké bien formé : version de poivre (1 à 9 chiffres, sans zéro initial) puis chaîne Argon2id. */
    private static final Pattern STORED_FORMAT = Pattern.compile("^p([1-9][0-9]{0,8})\\$(\\$argon2id\\$.+)$");

    private static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(1);

    private final PasswordPepper pepper;
    private final Argon2PasswordEncoder encoder;
    private final Semaphore permits;
    private final long acquireTimeoutNanos;
    private final String dummyHash;

    public Argon2PasswordHasher(PasswordPepper pepper, AuthProperties properties) {
        AuthProperties.Password p = properties.password();
        this.pepper = pepper;
        this.encoder = new Argon2PasswordEncoder(p.saltLength(), p.hashLength(), p.parallelism(), p.memoryKib(),
                p.iterations());
        this.permits = new Semaphore(p.maxConcurrentHashes(), true);
        this.acquireTimeoutNanos = p.hashAcquireTimeout().toNanos();
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.dummyHash = encoder.encode(Base64.getEncoder().encodeToString(random));
    }

    @Override
    public String hash(String rawPassword) {
        acquire();
        try {
            PasswordPepper.Pepper peppered = pepper.pepper(rawPassword);
            return "p" + peppered.keyVersion() + "$" + encoder.encode(peppered.value());
        } finally {
            permits.release();
        }
    }

    @Override
    public Verification verify(String rawPassword, String storedHash) {
        if (storedHash == null) {
            return Verification.MISMATCH;
        }
        Matcher matcher = STORED_FORMAT.matcher(storedHash);
        if (!matcher.matches()) {
            return Verification.MISMATCH;
        }
        int storedVersion = Integer.parseInt(matcher.group(1));
        String argon2 = matcher.group(2);

        acquire();
        try {
            PasswordPepper.Pepper latest = pepper.pepper(rawPassword);
            PasswordPepper.Pepper used = latest.keyVersion() == storedVersion
                    ? latest
                    : pepper.pepper(rawPassword, storedVersion);
            if (!encoder.matches(used.value(), argon2)) {
                return Verification.MISMATCH;
            }
            boolean rehash = storedVersion < latest.keyVersion() || encoder.upgradeEncoding(argon2);
            return rehash ? Verification.MATCH_REHASH_NEEDED : Verification.MATCH;
        } finally {
            permits.release();
        }
    }

    @Override
    public void dummyVerify(String rawPassword) {
        acquire();
        try {
            PasswordPepper.Pepper peppered = pepper.pepper(rawPassword);
            encoder.matches(peppered.value(), dummyHash);
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(acquireTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        if (!acquired) {
            throw busy();
        }
    }

    private static RetryLaterException busy() {
        return new RetryLaterException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "The service is busy. Retry later.", BUSY_RETRY_AFTER);
    }
}
