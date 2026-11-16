package be.bstorm.formation.framework.crypto;

import java.util.List;

/**
 * Colonne chiffrée à faire suivre une rotation de clé (une implémentation par colonne, ex. le NISS de l'utilisateur).
 * Utilisée par {@link KeyRotationService#rewrapAll}.
 */
public interface RewrapTarget {

    /** Nom affiché dans les logs (ex. {@code user.niss}). */
    String name();

    SensitiveDataType type();

    /**
     * Au plus {@code limit} lignes dont le chiffré a une version de clé strictement inférieure à {@code version}
     * (ex. en SQL : {@code split_part(colonne, ':', 2) <> 'v' || :version}, ou version extraite et comparée).
     */
    List<EncryptedValue> findOlderThan(int version, int limit);

    /** Remplace le chiffré de la ligne {@code id} (même transaction que la lecture du lot). */
    void update(String id, String newCiphertext);

    /**
     * @param id         identifiant de la ligne
     * @param ciphertext chiffré actuel
     * @param context    contexte utilisé au chiffrement (identifiant immuable de la ligne)
     */
    record EncryptedValue(String id, String ciphertext, String context) {
        @Override
        public String toString() {
            return "EncryptedValue[id=" + id + "]";
        }
    }
}
