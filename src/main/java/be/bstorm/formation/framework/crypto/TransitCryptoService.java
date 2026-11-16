package be.bstorm.formation.framework.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultHmacRequest;
import org.springframework.vault.support.VaultTransitContext;

import java.nio.charset.StandardCharsets;

/**
 * {@link CryptoService} adossé à OpenBao Transit : clés non exportables, l'API n'envoie que la donnée et son contexte.
 * Les erreurs sont reformulées sans aucune donnée (valeur, chiffré, contexte).
 */
@Service
@ConditionalOnBooleanProperty("app.openbao.enabled")
@RequiredArgsConstructor
public class TransitCryptoService implements CryptoService {

    private static final String HMAC_ALGORITHM = "sha2-256";

    private final VaultOperations vaultOperations;

    @Override
    public String encrypt(SensitiveDataType type, String plaintext, String context) {
        requireValue(plaintext);
        try {
            return transit().encrypt(type.encryptionKey(), plaintext.getBytes(StandardCharsets.UTF_8),
                    transitContext(type, context));
        } catch (VaultException e) {
            throw new CryptoException("Échec du chiffrement (" + type + ")", e);
        }
    }

    @Override
    public String decrypt(SensitiveDataType type, String ciphertext, String context) {
        CryptoService.keyVersion(ciphertext);
        try {
            byte[] plaintext = transit().decrypt(type.encryptionKey(), ciphertext, transitContext(type, context));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (VaultException e) {
            throw new CryptoException("Échec du déchiffrement (" + type + ") : chiffré altéré, version retirée "
                    + "ou contexte différent", e);
        }
    }

    @Override
    public String blindIndex(SensitiveDataType type, String normalizedValue) {
        requireValue(normalizedValue);
        try {
            VaultHmacRequest request = VaultHmacRequest.builder()
                    .plaintext(Plaintext.of(normalizedValue.getBytes(StandardCharsets.UTF_8)))
                    .algorithm(HMAC_ALGORITHM)
                    .build();
            return transit().getHmac(type.indexKey(), request).getHmac();
        } catch (VaultException e) {
            throw new CryptoException("Échec du calcul de l'index aveugle (" + type + ")", e);
        }
    }

    @Override
    public String rewrap(SensitiveDataType type, String ciphertext, String context) {
        CryptoService.keyVersion(ciphertext);
        try {
            return transit().rewrap(type.encryptionKey(), ciphertext, transitContext(type, context));
        } catch (VaultException e) {
            throw new CryptoException("Échec du re-chiffrement (" + type + ")", e);
        }
    }

    @Override
    public int latestKeyVersion(SensitiveDataType type) {
        try {
            return transit().getKey(type.encryptionKey()).getLatestVersion();
        } catch (VaultException e) {
            throw new CryptoException("Version de clé indisponible (" + type + ")", e);
        }
    }

    private VaultTransitOperations transit() {
        return vaultOperations.opsForTransit();
    }

    /** Contexte de dérivation : type de donnée + identifiant immuable de la ligne. */
    static VaultTransitContext transitContext(SensitiveDataType type, String context) {
        if (context == null || context.isBlank()) {
            throw new CryptoException("Contexte de chiffrement obligatoire (identifiant immuable de la ligne)");
        }
        return VaultTransitContext.fromContext((type.name() + ":" + context).getBytes(StandardCharsets.UTF_8));
    }

    private static void requireValue(String value) {
        if (value == null || value.isEmpty()) {
            throw new CryptoException("Valeur à protéger absente");
        }
    }
}
