package be.bstorm.formation.framework.security.jwt;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.security.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implémentation dev/prod de {@link JwtKeyProvider} : OpenBao Transit, clé {@value #KEY} (ecdsa-p256).
 * <ul>
 *   <li>signature : {@code transit/sign/jwt} ({@code key_version}, {@code hash_algorithm=sha2-256},
 *       {@code marshaling_algorithm=jws} → r||s) ;</li>
 *   <li>clés publiques : {@code transit/keys/jwt} (dernière version, versions >= {@code min_decryption_version}).</li>
 * </ul>
 * Cache (dernière version + clés publiques) :
 * <ul>
 *   <li>{@link #publicKey(String)} ne lit QUE le cache, ne déclenche jamais de chargement ;</li>
 *   <li>{@link #currentKeyId()} recharge le cache s'il est vide ou si le dernier chargement date d'au moins
 *       {@code app.auth.jwt.key-cache-refresh-interval} ; un seul rechargement à la fois ;</li>
 *   <li>chargement initial sur {@code ApplicationReadyEvent}, un échec y est journalisé (WARN) sans bloquer ;</li>
 *   <li>un rechargement en échec conserve le cache précédent (WARN) ; cache vide → {@link CryptoException}.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty("app.openbao.enabled")
public class TransitJwtKeyProvider implements JwtKeyProvider {

    public static final String KEY = "jwt";

    private static final String TRANSIT_PATH = "transit";
    private static final int P256_COORDINATE_LENGTH = 32;

    private final VaultOperations vaultOperations;
    private final Duration refreshInterval;
    private final Clock clock;
    private final Object reloadLock = new Object();

    private volatile KeyCache cache;

    private record KeyCache(String currentKeyId, Map<String, ECPublicKey> keys, Instant loadedAt) {
    }

    public TransitJwtKeyProvider(VaultOperations vaultOperations, AuthProperties properties, Clock clock) {
        this.vaultOperations = vaultOperations;
        this.refreshInterval = properties.jwt().keyCacheRefreshInterval();
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        try {
            synchronized (reloadLock) {
                cache = load();
            }
        } catch (RuntimeException e) {
            log.warn("Chargement initial des clés publiques JWT impossible : {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public String currentKeyId() {
        KeyCache current = cache;
        if (current == null || isStale(current)) {
            current = reload();
        }
        return current.currentKeyId();
    }

    @Override
    public byte[] sign(String keyId, byte[] signingInput) {
        int version = parseVersion(keyId);
        Map<String, Object> request = new HashMap<>();
        request.put("input", Base64.getEncoder().encodeToString(signingInput));
        request.put("key_version", version);
        request.put("hash_algorithm", "sha2-256");
        request.put("marshaling_algorithm", "jws");
        VaultResponse response;
        try {
            response = vaultOperations.write(TRANSIT_PATH + "/sign/" + KEY, request);
        } catch (VaultException e) {
            throw new CryptoException("Échec de la signature du jeton d'accès", e);
        }
        if (response == null || response.getData() == null || !(response.getData().get("signature") instanceof String s)) {
            throw new CryptoException("Réponse de signature invalide");
        }
        return decodeSignature(s);
    }

    @Override
    public Optional<ECPublicKey> publicKey(String keyId) {
        KeyCache current = cache;
        if (current == null || keyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.keys().get(keyId));
    }

    private boolean isStale(KeyCache current) {
        return !clock.instant().isBefore(current.loadedAt().plus(refreshInterval));
    }

    private KeyCache reload() {
        synchronized (reloadLock) {
            KeyCache current = cache;
            if (current != null && !isStale(current)) {
                return current;   // rechargé entre-temps par un autre thread
            }
            try {
                KeyCache loaded = load();
                cache = loaded;
                return loaded;
            } catch (RuntimeException e) {
                if (current != null) {
                    log.warn("Rechargement des clés publiques JWT impossible, cache précédent conservé : {}",
                            e.getClass().getSimpleName());
                    return current;
                }
                throw e instanceof CryptoException ce ? ce
                        : new CryptoException("Clés de signature JWT indisponibles", e);
            }
        }
    }

    private KeyCache load() {
        VaultResponse response;
        try {
            response = vaultOperations.read(TRANSIT_PATH + "/keys/" + KEY);
        } catch (VaultException e) {
            throw new CryptoException("Lecture des clés JWT impossible", e);
        }
        if (response == null || response.getData() == null) {
            throw new CryptoException("Clé Transit JWT introuvable");
        }
        Map<String, Object> data = response.getData();
        int latest = intValue(data.get("latest_version"));
        Object minObject = data.get("min_decryption_version");
        int min = minObject == null ? 1 : intValue(minObject);
        if (!(data.get("keys") instanceof Map<?, ?> versions)) {
            throw new CryptoException("Clés publiques JWT absentes");
        }
        Map<String, ECPublicKey> keys = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : versions.entrySet()) {
            String kid = String.valueOf(entry.getKey());
            int version;
            try {
                version = Integer.parseInt(kid);
            } catch (NumberFormatException e) {
                continue;
            }
            if (version < min || !(entry.getValue() instanceof Map<?, ?> keyData)
                    || !(keyData.get("public_key") instanceof String pem)) {
                continue;
            }
            keys.put(Integer.toString(version), parsePublicKey(pem));
        }
        String currentKid = Integer.toString(latest);
        if (!keys.containsKey(currentKid)) {
            throw new CryptoException("Clé publique JWT courante absente");
        }
        return new KeyCache(currentKid, Map.copyOf(keys), clock.instant());
    }

    private static int intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new CryptoException("Version de clé JWT invalide");
            }
        }
        throw new CryptoException("Version de clé JWT absente");
    }

    private static int parseVersion(String keyId) {
        try {
            int version = Integer.parseInt(keyId);
            if (version < 1) {
                throw new CryptoException("Version de clé JWT invalide");
            }
            return version;
        } catch (NumberFormatException | NullPointerException e) {
            throw new CryptoException("Version de clé JWT invalide");
        }
    }

    private static ECPublicKey parsePublicKey(String pem) {
        try {
            String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof ECPublicKey ec)) {
                throw new CryptoException("Clé publique JWT non EC");
            }
            return ec;
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Clé publique JWT illisible", e);
        }
    }

    /** {@code vault:v1:<base64url r||s>} → 64 octets (conversion DER → P1363 si nécessaire). */
    static byte[] decodeSignature(String signature) {
        int second = signature.indexOf(':', signature.indexOf(':') + 1);
        String encoded = second < 0 ? signature : signature.substring(second + 1);
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            try {
                raw = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException e2) {
                throw new CryptoException("Signature JWT illisible");
            }
        }
        if (raw.length == 2 * P256_COORDINATE_LENGTH) {
            return raw;
        }
        return derToP1363(raw);
    }

    private static byte[] derToP1363(byte[] der) {
        try {
            int offset = 0;
            if (der[offset++] != 0x30) {
                throw new CryptoException("Signature JWT invalide");
            }
            int seqLength = der[offset++] & 0xff;
            if (seqLength > 0x80) {
                offset += seqLength - 0x80;
            }
            if (der[offset++] != 0x02) {
                throw new CryptoException("Signature JWT invalide");
            }
            int rLength = der[offset++] & 0xff;
            BigInteger r = new BigInteger(1, java.util.Arrays.copyOfRange(der, offset, offset + rLength));
            offset += rLength;
            if (der[offset++] != 0x02) {
                throw new CryptoException("Signature JWT invalide");
            }
            int sLength = der[offset++] & 0xff;
            BigInteger s = new BigInteger(1, java.util.Arrays.copyOfRange(der, offset, offset + sLength));
            byte[] out = new byte[2 * P256_COORDINATE_LENGTH];
            copyUnsigned(r, out, 0);
            copyUnsigned(s, out, P256_COORDINATE_LENGTH);
            return out;
        } catch (CryptoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CryptoException("Signature JWT invalide", e);
        }
    }

    private static void copyUnsigned(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int start = bytes.length > P256_COORDINATE_LENGTH ? bytes.length - P256_COORDINATE_LENGTH : 0;
        int length = bytes.length - start;
        if (length > P256_COORDINATE_LENGTH) {
            throw new CryptoException("Signature JWT invalide");
        }
        System.arraycopy(bytes, start, out, offset + P256_COORDINATE_LENGTH - length, length);
    }
}
