package be.bstorm.formation.framework.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link CryptoService} en mémoire pour les tests sans OpenBao — <b>jamais en production</b> (src/test uniquement).
 * Même contrat que Transit : AES-256-GCM avec le contexte en données associées, versions de clé, rotation, rewrap,
 * HMAC-SHA256 déterministe. Préfixe {@code local:} pour ne jamais confondre avec un chiffré réel.
 */
public class InMemoryCryptoService implements CryptoService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 12;

    private final Map<SensitiveDataType, List<SecretKey>> encryptionKeys = new EnumMap<>(SensitiveDataType.class);
    private final Map<SensitiveDataType, SecretKey> indexKeys = new EnumMap<>(SensitiveDataType.class);
    private final Map<SensitiveDataType, Integer> minDecryptionVersion = new EnumMap<>(SensitiveDataType.class);

    public InMemoryCryptoService() {
        for (SensitiveDataType type : SensitiveDataType.values()) {
            encryptionKeys.put(type, new ArrayList<>(List.of(newKey("AES"))));
            indexKeys.put(type, newKey("HmacSHA256"));
            minDecryptionVersion.put(type, 1);
        }
    }

    /** Équivalent de {@code transit/keys/<clé>/rotate}. */
    public synchronized void rotate(SensitiveDataType type) {
        encryptionKeys.get(type).add(newKey("AES"));
    }

    /** Équivalent de {@code min_decryption_version}. */
    public synchronized void setMinDecryptionVersion(SensitiveDataType type, int version) {
        minDecryptionVersion.put(type, version);
    }

    @Override
    public synchronized String encrypt(SensitiveDataType type, String plaintext, String context) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new CryptoException("Valeur à protéger absente");
        }
        int version = latestKeyVersion(type);
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(type, version), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(type, context));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "local:v" + version + ":" + Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Échec du chiffrement (" + type + ")", e);
        }
    }

    @Override
    public synchronized String decrypt(SensitiveDataType type, String ciphertext, String context) {
        int version = CryptoService.keyVersion(ciphertext);
        if (!ciphertext.startsWith("local:") || version < minDecryptionVersion.get(type)
                || version > latestKeyVersion(type)) {
            throw new CryptoException("Échec du déchiffrement (" + type + ")");
        }
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext.substring(ciphertext.indexOf(':', 6) + 1));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(type, version), new GCMParameterSpec(128, data, 0, IV_LENGTH));
            cipher.updateAAD(aad(type, context));
            return new String(cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new CryptoException("Échec du déchiffrement (" + type + ")", e);
        }
    }

    @Override
    public synchronized String blindIndex(SensitiveDataType type, String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            throw new CryptoException("Valeur à protéger absente");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKeys.get(type));
            return "local:v1:" + Base64.getEncoder()
                    .encodeToString(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Échec du calcul de l'index aveugle (" + type + ")", e);
        }
    }

    @Override
    public synchronized String rewrap(SensitiveDataType type, String ciphertext, String context) {
        return encrypt(type, decrypt(type, ciphertext, context), context);
    }

    @Override
    public synchronized int latestKeyVersion(SensitiveDataType type) {
        return encryptionKeys.get(type).size();
    }

    private SecretKey key(SensitiveDataType type, int version) {
        return encryptionKeys.get(type).get(version - 1);
    }

    private static byte[] aad(SensitiveDataType type, String context) {
        if (context == null || context.isBlank()) {
            throw new CryptoException("Contexte de chiffrement obligatoire (identifiant immuable de la ligne)");
        }
        return (type.name() + ":" + context).getBytes(StandardCharsets.UTF_8);
    }

    private static SecretKey newKey(String algorithm) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(algorithm);
            generator.init(256);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
