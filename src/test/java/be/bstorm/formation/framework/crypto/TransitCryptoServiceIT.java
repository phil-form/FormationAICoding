package be.bstorm.formation.framework.crypto;

import be.bstorm.formation.framework.openbao.OpenBaoTestEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du {@link CryptoService} contre OpenBao Transit réel, avec un token de l'AppRole {@code api} : la politique
 * de production suffit (et rien de plus). Les rotations sont faites avec le client administrateur.
 */
class TransitCryptoServiceIT extends CryptoServiceContract {

    private final VaultTemplate admin = OpenBaoTestEnvironment.admin();
    private final VaultTemplate api = OpenBaoTestEnvironment.api();
    private final TransitCryptoService crypto = new TransitCryptoService(api);

    @Override
    protected CryptoService crypto() {
        return crypto;
    }

    @Override
    protected void rotateKey(SensitiveDataType type) {
        admin.opsForTransit().rotate(type.encryptionKey());
    }

    @Override
    protected void setMinDecryptionVersion(SensitiveDataType type, int version) {
        admin.write("transit/keys/" + type.encryptionKey() + "/config", Map.of("min_decryption_version", version));
    }

    @Test
    void ciphertextsAreRealTransitCiphertexts() {
        assertThat(crypto.encrypt(SensitiveDataType.NISS, NISS_VALUE, ROW_A)).startsWith("vault:v");
        assertThat(crypto.blindIndex(SensitiveDataType.NISS, NISS_VALUE)).startsWith("vault:v1:");
    }

    @Test
    void fileDataKeysUseTheDerivedFileKeyBoundToTheFile() {
        String dataKey = "q3Jx3m0bS2kY8zQd7l1n0f5hV9pW2cR4tA6uE8iO0yI=";
        String wrapped = crypto.encrypt(SensitiveDataType.FILE, dataKey, ROW_A);

        assertThat(wrapped).startsWith("vault:v");
        assertThat(crypto.decrypt(SensitiveDataType.FILE, wrapped, ROW_A)).isEqualTo(dataKey);
        assertThatThrownBy(() -> crypto.decrypt(SensitiveDataType.FILE, wrapped, ROW_B)).isInstanceOf(CryptoException.class);
        assertThat(crypto.rewrap(SensitiveDataType.FILE, wrapped, ROW_A)).startsWith("vault:v");
        assertThatThrownBy(() -> api.opsForTransit().rotate("file")).hasMessageContaining("permission denied");
    }

    @Test
    void applicationCannotRotateOrExportKeys() {
        assertThatThrownBy(() -> api.opsForTransit().rotate("niss")).hasMessageContaining("permission denied");
        assertThatThrownBy(() -> api.read("transit/export/encryption-key/niss"))
                .hasMessageContaining("permission denied");
    }
}
