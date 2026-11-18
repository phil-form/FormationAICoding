package be.bstorm.formation.framework.security.jwt;

import be.bstorm.formation.framework.security.AuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.Key;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Vérification des jetons d'accès (RG-07, § 5.8), locale : clés publiques lues dans le cache de
 * {@link JwtKeyProvider} (aucun appel OpenBao, aucun accès base par requête).
 * <ul>
 *   <li>algorithme imposé : ES256 uniquement (rejet de {@code none}, HS256…) ; {@code kid} obligatoire et connu ;</li>
 *   <li>vérificateur de claims Nimbus neutralisé (il tolère 60 s) et pas de {@code JwtTimestampValidator} (il accepte
 *       {@code now == exp}) : validateur dédié, tolérance 0, {@code now < exp}, {@code iat <= now} ;</li>
 *   <li>{@code iss} et {@code aud} égaux à la configuration.</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    @Bean
    JwtDecoder jwtDecoder(JwtKeyProvider keyProvider, AuthProperties properties, Clock clock) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector(keyProvider));
        processor.setJWTClaimsSetVerifier((claims, context) -> {
            // Toute la validation des claims passe par les OAuth2TokenValidator ci-dessous.
        });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        AuthProperties.Jwt jwt = properties.jwt();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timeValidator(clock),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        iss -> iss != null && jwt.issuer().equals(iss.toString())),
                new JwtClaimValidator<Object>(JwtClaimNames.AUD,
                        aud -> audienceMatches(aud, jwt.audience()))));
        return decoder;
    }

    /** ES256 seulement ; clé du {@code kid} lue dans le cache ; toute erreur du fournisseur → aucune clé (401). */
    static JWSKeySelector<SecurityContext> keySelector(JwtKeyProvider keyProvider) {
        return (JWSHeader header, SecurityContext context) -> {
            if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
                return List.of();
            }
            String kid = header.getKeyID();
            if (kid == null || kid.isBlank()) {
                return List.of();
            }
            try {
                Optional<ECPublicKey> key = keyProvider.publicKey(kid);
                if (key == null || key.isEmpty()) {
                    return List.<Key>of();
                }
                return List.<Key>of(key.get());
            } catch (RuntimeException e) {
                log.warn("Clé publique JWT indisponible (kid={}) : {}", kid.matches("\\d{1,9}") ? kid : "?",
                        e.getClass().getSimpleName());
                return List.of();
            }
        };
    }

    /** {@code exp} et {@code iat} présents, {@code now < exp} (exclusif), {@code iat <= now}, tolérance 0. */
    static OAuth2TokenValidator<Jwt> timeValidator(Clock clock) {
        return token -> {
            Instant now = clock.instant();
            Instant exp = token.getExpiresAt();
            Instant iat = token.getIssuedAt();
            if (exp == null || iat == null || !now.isBefore(exp) || iat.isAfter(now)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Invalid token lifetime", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static boolean audienceMatches(Object aud, String expected) {
        if (aud instanceof String s) {
            return expected.equals(s);
        }
        if (aud instanceof Collection<?> values) {
            return values.stream().anyMatch(v -> v != null && expected.equals(v.toString()));
        }
        return false;
    }
}
