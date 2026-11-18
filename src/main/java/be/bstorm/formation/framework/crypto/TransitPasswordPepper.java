package be.bstorm.formation.framework.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultHmacRequest;

import java.nio.charset.StandardCharsets;

/**
 * {@link PasswordPepper} adossé à OpenBao Transit ({@code transit/hmac/password-pepper}, SHA-256).
 * Les erreurs ne contiennent jamais le mot de passe.
 */
@Service
@ConditionalOnBooleanProperty("app.openbao.enabled")
@RequiredArgsConstructor
public class TransitPasswordPepper implements PasswordPepper {

    static final String KEY = "password-pepper";
    private static final String HMAC_ALGORITHM = "sha2-256";

    private final VaultOperations vaultOperations;

    @Override
    public Pepper pepper(String password) {
        return hmac(password, null);
    }

    @Override
    public Pepper pepper(String password, int keyVersion) {
        if (keyVersion < 1) {
            throw new CryptoException("Version de clé de poivre invalide");
        }
        return hmac(password, keyVersion);
    }

    private Pepper hmac(String password, Integer keyVersion) {
        if (password == null || password.isEmpty()) {
            throw new CryptoException("Mot de passe absent");
        }
        try {
            VaultHmacRequest.VaultHmacRequestBuilder request = VaultHmacRequest.builder()
                    .plaintext(Plaintext.of(password.getBytes(StandardCharsets.UTF_8)))
                    .algorithm(HMAC_ALGORITHM);
            if (keyVersion != null) {
                request.keyVersion(keyVersion);
            }
            return parse(vaultOperations.opsForTransit().getHmac(KEY, request.build()).getHmac());
        } catch (VaultException e) {
            throw new CryptoException("Échec du calcul du poivre", e);
        }
    }

    /** {@code vault:v2:<base64>} → (2, base64). */
    static Pepper parse(String hmac) {
        int version = CryptoService.keyVersion(hmac);
        return new Pepper(version, hmac.substring(hmac.indexOf(':', hmac.indexOf(':') + 1) + 1));
    }
}
