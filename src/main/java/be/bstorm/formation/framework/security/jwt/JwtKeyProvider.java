package be.bstorm.formation.framework.security.jwt;

import java.security.interfaces.ECPublicKey;
import java.util.Optional;

/**
 * Clés de signature des jetons d'accès (ADR 0001 D4).
 * Aucune implémentation en mémoire dans src/main : en profil test, TestcontainersConfig fournit InMemoryJwtKeyProvider.
 */
public interface JwtKeyProvider {

    /** kid de la clé courante = version Transit en décimal, ex. "1". */
    String currentKeyId();

    /** Signature ES256 (r||s, 64 octets) de signingInput (octets ASCII de "&lt;header b64url&gt;.&lt;payload b64url&gt;"). */
    byte[] sign(String keyId, byte[] signingInput);

    /**
     * Clé publique P-256 du kid ; vide si inconnu ou retiré. Lit le cache uniquement : jamais d'appel réseau
     * (appelée à chaque requête authentifiée).
     */
    Optional<ECPublicKey> publicKey(String keyId);
}
