package be.bstorm.formation.framework.security.jwt;

import be.bstorm.formation.framework.security.AuthProperties;
import be.bstorm.formation.framework.security.CurrentUser;
import be.bstorm.formation.framework.security.Role;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Émission des jetons d'accès (RG-06, § 5.8) : JWT compact JWS ES256 signé par {@link JwtKeyProvider} (Transit).
 * Claims : {@code iss}, {@code aud} (chaîne), {@code sub}, {@code iat}, {@code exp}, {@code jti}, {@code sid},
 * {@code roles} (ordre de déclaration), {@code depot_id} (absent si null). Aucune autre claim (SD-04).
 */
@Component
public class AccessTokenIssuer {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_DEPOT_ID = "depot_id";
    public static final String CLAIM_SID = "sid";

    private final JwtKeyProvider keyProvider;
    private final AuthProperties.Jwt properties;

    public AccessTokenIssuer(JwtKeyProvider keyProvider, AuthProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties.jwt();
    }

    /** Durée de vie du jeton en secondes ({@code expiresIn}). */
    public long ttlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /**
     * Signe un jeton d'accès. Appel OpenBao (hors transaction, SEC-11) ; une erreur est propagée ({@code CryptoException}).
     *
     * @param now instant de l'émission ; {@code iat} = secondes epoch (fraction tronquée)
     */
    public String issue(CurrentUser user, UUID sessionId, Instant now) {
        long iat = now.getEpochSecond();
        long exp = iat + ttlSeconds();
        List<String> roles = user.roles().stream().sorted().map(Role::name).toList();

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .audience(properties.audience())
                .subject(user.id().toString())
                .issueTime(Date.from(Instant.ofEpochSecond(iat)))
                .expirationTime(Date.from(Instant.ofEpochSecond(exp)))
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_SID, sessionId.toString())
                .claim(CLAIM_ROLES, roles);
        if (user.depotId() != null) {
            claims.claim(CLAIM_DEPOT_ID, user.depotId().toString());
        }

        String keyId = keyProvider.currentKeyId();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(JOSEObjectType.JWT)
                .keyID(keyId)
                .build();
        String signingInput = header.toBase64URL() + "." + claims.build().toPayload().toBase64URL();
        byte[] signature = keyProvider.sign(keyId, signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64URL.encode(signature);
    }
}
