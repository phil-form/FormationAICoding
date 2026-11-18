package be.bstorm.formation.framework.security.jwt;

import be.bstorm.formation.framework.crypto.CryptoException;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link JwtKeyProvider} en mémoire pour les tests sans OpenBao — <b>jamais en production</b> (src/test uniquement).
 * Même contrat que Transit : paires P-256 versionnées ({@code kid} = version décimale, {@code "1"} au départ),
 * signature ES256 r||s (64 octets), rotation qui conserve les versions précédentes.
 */
public class InMemoryJwtKeyProvider implements JwtKeyProvider {

    private final List<KeyPair> keys = new CopyOnWriteArrayList<>(List.of(newKeyPair()));

    /** Équivalent de {@code transit/keys/jwt/rotate} : nouvelle version courante, anciennes conservées. */
    public synchronized void rotate() {
        keys.add(newKeyPair());
    }

    @Override
    public String currentKeyId() {
        return String.valueOf(keys.size());
    }

    @Override
    public byte[] sign(String keyId, byte[] signingInput) {
        KeyPair pair = find(keyId).orElseThrow(() -> new CryptoException("Unknown signing key version"));
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(pair.getPrivate());
            signature.update(signingInput);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Signing failed", e);
        }
    }

    @Override
    public Optional<ECPublicKey> publicKey(String keyId) {
        return find(keyId).map(pair -> (ECPublicKey) pair.getPublic());
    }

    private Optional<KeyPair> find(String keyId) {
        if (keyId == null || !keyId.matches("[1-9][0-9]{0,8}")) {
            return Optional.empty();
        }
        int version = Integer.parseInt(keyId);
        return version <= keys.size() ? Optional.of(keys.get(version - 1)) : Optional.empty();
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
