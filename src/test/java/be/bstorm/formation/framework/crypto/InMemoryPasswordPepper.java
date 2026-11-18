package be.bstorm.formation.framework.crypto;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * {@link PasswordPepper} en mémoire pour les tests sans OpenBao — <b>jamais en production</b> (src/test uniquement).
 * Même contrat que Transit : HMAC-SHA256 déterministe par version de clé, rotation.
 */
public class InMemoryPasswordPepper implements PasswordPepper {

    private final List<SecretKey> keys = new ArrayList<>(List.of(newKey()));

    /** Équivalent de {@code transit/keys/password-pepper/rotate}. */
    public synchronized void rotate() {
        keys.add(newKey());
    }

    @Override
    public synchronized Pepper pepper(String password) {
        return pepper(password, keys.size());
    }

    @Override
    public synchronized Pepper pepper(String password, int keyVersion) {
        if (password == null || password.isEmpty()) {
            throw new CryptoException("Mot de passe absent");
        }
        if (keyVersion < 1 || keyVersion > keys.size()) {
            throw new CryptoException("Version de clé de poivre invalide");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keys.get(keyVersion - 1));
            return new Pepper(keyVersion,
                    Base64.getEncoder().encodeToString(mac.doFinal(password.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Échec du calcul du poivre", e);
        }
    }

    private static SecretKey newKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
            generator.init(256);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
