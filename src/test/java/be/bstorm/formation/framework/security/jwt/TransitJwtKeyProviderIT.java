package be.bstorm.formation.framework.security.jwt;

import be.bstorm.formation.framework.openbao.OpenBaoTestEnvironment;
import be.bstorm.formation.support.MutableClock;
import be.bstorm.formation.support.TestAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultTemplate;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 001 — AC-57 : {@link TransitJwtKeyProvider} contre OpenBao Transit réel (clé {@code jwt} ecdsa-p256), avec un
 * token de l'AppRole {@code api} ; la rotation est faite avec le client administrateur. Versions comparées en relatif
 * (environnement OpenBao partagé par la JVM de test).
 */
@Tag("feature-001")
class TransitJwtKeyProviderIT {

    private static final byte[] SIGNING_INPUT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0cmFuc2l0LXRlc3QifQ".getBytes(StandardCharsets.US_ASCII);

    private final VaultTemplate admin = OpenBaoTestEnvironment.admin();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-15T08:00:00Z"));
    private final TransitJwtKeyProvider provider =
            new TransitJwtKeyProvider(OpenBaoTestEnvironment.api(), TestAuthProperties.defaults(), clock);

    @Test
    @DisplayName("[AC-57] sign(currentKeyId) : 64 octets r||s vérifiables avec publicKey(currentKeyId)")
    void ac57_signatureVerifiesWithCachedPublicKey() throws Exception {
        // Given
        String kid = provider.currentKeyId();

        // When
        byte[] signature = provider.sign(kid, SIGNING_INPUT);

        // Then
        assertThat(signature).hasSize(64);
        assertThat(verifies(provider.publicKey(kid).orElseThrow(), signature)).isTrue();
    }

    @Test
    @DisplayName("[AC-57] publicKey d'un kid inconnu : vide")
    void ac57_unknownKidHasNoPublicKey() {
        // Given
        provider.currentKeyId();

        // When / Then
        assertThat(provider.publicKey("999")).isEmpty();
    }

    @Test
    @DisplayName("[AC-57] Rotation : publicKey ne recharge jamais le cache, currentKeyId le recharge ; l'ancien kid reste vérifiable")
    void ac57_rotationIsPickedUpOnlyThroughCurrentKeyId() throws Exception {
        // Given
        String oldKid = provider.currentKeyId();
        byte[] oldSignature = provider.sign(oldKid, SIGNING_INPUT);
        String newKid = String.valueOf(Integer.parseInt(oldKid) + 1);

        // When
        admin.opsForTransit().rotate(TransitJwtKeyProvider.KEY);
        clock.advance(Duration.ofSeconds(61));

        // Then : publicKey seul ne voit pas la nouvelle version
        assertThat(provider.publicKey(newKid)).isEmpty();

        // When
        String current = provider.currentKeyId();

        // Then
        assertThat(current).isEqualTo(newKid);
        assertThat(provider.publicKey(newKid)).isPresent();
        assertThat(verifies(provider.publicKey(oldKid).orElseThrow(), oldSignature)).isTrue();
    }

    private static boolean verifies(ECPublicKey key, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(key);
        verifier.update(SIGNING_INPUT);
        return verifier.verify(signature);
    }
}
