package be.bstorm.formation.framework.crypto;

/**
 * Poivre des mots de passe (ADR 0002, D5) : {@code HMAC-SHA256(clé Transit password-pepper, mot de passe)}, calculé par
 * OpenBao — la clé n'est jamais exportable, une fuite de la base seule ne permet aucune attaque hors ligne.
 *
 * <p>Le résultat est ensuite haché (Argon2id) ; le hachage stocké mémorise la {@link Pepper#keyVersion() version} de
 * la clé pour pouvoir vérifier un ancien hachage après rotation, puis re-hacher avec la dernière version.
 *
 * <p>Ne jamais journaliser le mot de passe ni le poivre.
 */
public interface PasswordPepper {

    /** Poivre avec la dernière version de la clé. */
    Pepper pepper(String password);

    /**
     * Poivre avec une version précise de la clé (vérification d'un hachage existant).
     *
     * @throws CryptoException si la version n'existe pas ou n'est plus autorisée
     */
    Pepper pepper(String password, int keyVersion);

    /**
     * @param keyVersion version de la clé {@code password-pepper} utilisée
     * @param value      HMAC encodé en base64 (sans préfixe), entrée de la fonction de hachage
     */
    record Pepper(int keyVersion, String value) {

        @Override
        public String toString() {
            return "Pepper[keyVersion=" + keyVersion + ", value=***]";
        }
    }
}
