package be.bstorm.formation.framework.security.password;

/**
 * Hachage des mots de passe (RG-14, ADR 0002 D4/D5) : poivre Transit puis Argon2id ; format stocké
 * {@code p<version de poivre>$<chaîne Argon2id>}.
 */
public interface PasswordHasher {

    /** RG-14 : "p&lt;version&gt;$" + Argon2id(pepper(raw)). Utilisé par le seeder, le re-hachage et la feature 002. */
    String hash(String rawPassword);

    /** RG-14 : hachage mal formé → MISMATCH sans appel au poivre ; version de poivre refusée → CryptoException. */
    Verification verify(String rawPassword, String storedHash);

    /**
     * RG-03 : sémaphore RG-21 + pepper.pepper(raw) (un appel) + encoder.matches(pepper.value(), dummyHash).
     * Ne renvoie rien ; propage CryptoException / RetryLaterException comme verify.
     */
    void dummyVerify(String rawPassword);

    enum Verification { MISMATCH, MATCH, MATCH_REHASH_NEEDED }
}
