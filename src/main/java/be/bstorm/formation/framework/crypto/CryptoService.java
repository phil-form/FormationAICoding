package be.bstorm.formation.framework.crypto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chiffrement des données sensibles (ex. NISS) — l'API ne détient jamais de clé (OpenBao Transit en dev/prod).
 *
 * <h2>Contexte (liaison à la ligne)</h2>
 * Chaque chiffré est lié à un {@code context} : l'identifiant <b>immuable</b> de la ligne qui le porte (ex. l'UUID de
 * l'entité, à générer avant le chiffrement). Déchiffrer avec un autre contexte échoue : un chiffré copié d'une ligne
 * vers une autre (accès en écriture à la base) devient illisible. Le type de donnée fait partie du contexte.
 *
 * <h2>Index aveugle</h2>
 * {@link #blindIndex} est déterministe (HMAC) : à stocker dans une colonne unique pour rechercher une valeur exacte
 * ou garantir son unicité, sans la stocker en clair. Toujours l'appliquer à une valeur <b>normalisée</b>
 * (ex. {@code Niss#digits()}).
 *
 * <h2>Rotation</h2>
 * Les chiffrés portent leur version de clé ({@code vault:v3:…}). Après rotation de la clé, {@link #rewrap} les
 * re-chiffre avec la dernière version sans jamais exposer la valeur en clair (voir {@code KeyRotationService}).
 */
public interface CryptoService {

    String encrypt(SensitiveDataType type, String plaintext, String context);

    /** @throws CryptoException si le chiffré est altéré, d'une version retirée ou d'un autre contexte */
    String decrypt(SensitiveDataType type, String ciphertext, String context);

    /** Empreinte déterministe d'une valeur normalisée (recherche exacte, contrainte d'unicité). */
    String blindIndex(SensitiveDataType type, String normalizedValue);

    /** Re-chiffre avec la dernière version de la clé (la valeur en clair ne sort pas du coffre). */
    String rewrap(SensitiveDataType type, String ciphertext, String context);

    /** Dernière version de la clé de chiffrement du type. */
    int latestKeyVersion(SensitiveDataType type);

    Pattern VERSIONED = Pattern.compile("^[a-z]+:v(\\d+):.+$");

    /** Version de clé d'un chiffré ({@code vault:v3:…} → 3). */
    static int keyVersion(String ciphertext) {
        Matcher matcher = VERSIONED.matcher(ciphertext == null ? "" : ciphertext);
        if (!matcher.matches()) {
            throw new CryptoException("Format de chiffré invalide");
        }
        return Integer.parseInt(matcher.group(1));
    }
}
