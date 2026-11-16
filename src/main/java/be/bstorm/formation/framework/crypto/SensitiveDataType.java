package be.bstorm.formation.framework.crypto;

/**
 * Types de données sensibles protégées par le {@link CryptoService}, chacun avec ses clés Transit (ADR 0001, D6).
 * Ajouter un type = ajouter ses clés dans {@code docker/openbao/configure.sh} et les droits dans la politique {@code api}.
 *
 * @param encryptionKey clé AES-256-GCM96 dérivée (chiffré lié à un contexte)
 * @param indexKey      clé HMAC de l'index aveugle (recherche / unicité), {@code null} si non indexé
 */
public enum SensitiveDataType {

    NISS("niss", "niss-index"),

    /** Clés de données des fichiers téléversés (chiffrement par enveloppe, {@code framework.storage.FileStorage}). */
    FILE("file", null);

    private final String encryptionKey;
    private final String indexKey;

    SensitiveDataType(String encryptionKey, String indexKey) {
        this.encryptionKey = encryptionKey;
        this.indexKey = indexKey;
    }

    public String encryptionKey() {
        return encryptionKey;
    }

    public String indexKey() {
        if (indexKey == null) {
            throw new UnsupportedOperationException(name() + " n'a pas d'index aveugle");
        }
        return indexKey;
    }
}
