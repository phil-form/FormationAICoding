package be.bstorm.formation.framework.crypto;

import org.junit.jupiter.api.Test;

import static be.bstorm.formation.framework.crypto.SensitiveDataType.NISS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du {@link CryptoService}, exécuté sur chaque implémentation (en mémoire, OpenBao Transit) : les tests
 * des features peuvent ainsi utiliser l'implémentation en mémoire avec les mêmes garanties.
 */
abstract class CryptoServiceContract {

    protected static final String NISS_VALUE = "85073003328";
    protected static final String ROW_A = "0193a5f2-1c3e-7a10-9b2c-111111111111";
    protected static final String ROW_B = "0193a5f2-1c3e-7a10-9b2c-222222222222";

    protected abstract CryptoService crypto();

    /** Rotation de la clé de chiffrement (opération d'administration). */
    protected abstract void rotateKey(SensitiveDataType type);

    /** Interdit le déchiffrement des versions antérieures à {@code version}. */
    protected abstract void setMinDecryptionVersion(SensitiveDataType type, int version);

    @Test
    void roundTrip() {
        String ciphertext = crypto().encrypt(NISS, NISS_VALUE, ROW_A);

        assertThat(ciphertext).doesNotContain(NISS_VALUE).matches("^[a-z]+:v\\d+:.+$");
        assertThat(crypto().decrypt(NISS, ciphertext, ROW_A)).isEqualTo(NISS_VALUE);
    }

    @Test
    void encryptionIsNotDeterministic() {
        assertThat(crypto().encrypt(NISS, NISS_VALUE, ROW_A)).isNotEqualTo(crypto().encrypt(NISS, NISS_VALUE, ROW_A));
    }

    @Test
    void ciphertextIsBoundToItsRow() {
        String ciphertext = crypto().encrypt(NISS, NISS_VALUE, ROW_A);

        assertThatThrownBy(() -> crypto().decrypt(NISS, ciphertext, ROW_B))
                .isInstanceOf(CryptoException.class)
                .hasMessageNotContaining(NISS_VALUE).hasMessageNotContaining(ciphertext).hasMessageNotContaining(ROW_B);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String ciphertext = crypto().encrypt(NISS, NISS_VALUE, ROW_A);
        char last = ciphertext.charAt(ciphertext.length() - 2);
        String tampered = ciphertext.substring(0, ciphertext.length() - 2) + (last == 'A' ? 'B' : 'A')
                + ciphertext.charAt(ciphertext.length() - 1);

        assertThatThrownBy(() -> crypto().decrypt(NISS, tampered, ROW_A)).isInstanceOf(CryptoException.class);
    }

    @Test
    void contextIsMandatory() {
        assertThatThrownBy(() -> crypto().encrypt(NISS, NISS_VALUE, " ")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> crypto().encrypt(NISS, NISS_VALUE, null)).isInstanceOf(CryptoException.class);
    }

    @Test
    void emptyValueIsRejected() {
        assertThatThrownBy(() -> crypto().encrypt(NISS, "", ROW_A)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> crypto().blindIndex(NISS, null)).isInstanceOf(CryptoException.class);
    }

    @Test
    void blindIndexIsDeterministicDistinctAndOpaque() {
        String index = crypto().blindIndex(NISS, NISS_VALUE);

        assertThat(crypto().blindIndex(NISS, NISS_VALUE)).isEqualTo(index);
        assertThat(crypto().blindIndex(NISS, "05031212459")).isNotEqualTo(index);
        assertThat(index).doesNotContain(NISS_VALUE);
    }

    @Test
    void rotationRewrapAndMinimumVersion() {
        String v1 = crypto().encrypt(NISS, NISS_VALUE, ROW_A);
        int before = CryptoService.keyVersion(v1);

        rotateKey(NISS);
        int latest = crypto().latestKeyVersion(NISS);
        String rewrapped = crypto().rewrap(NISS, v1, ROW_A);

        assertThat(latest).isEqualTo(before + 1);
        assertThat(CryptoService.keyVersion(rewrapped)).isEqualTo(latest);
        assertThat(crypto().decrypt(NISS, rewrapped, ROW_A)).isEqualTo(NISS_VALUE);
        assertThat(crypto().decrypt(NISS, v1, ROW_A)).isEqualTo(NISS_VALUE);

        setMinDecryptionVersion(NISS, latest);
        assertThatThrownBy(() -> crypto().decrypt(NISS, v1, ROW_A)).isInstanceOf(CryptoException.class);
        assertThat(crypto().decrypt(NISS, rewrapped, ROW_A)).isEqualTo(NISS_VALUE);
    }

    @Test
    void keyVersionParsing() {
        assertThat(CryptoService.keyVersion("vault:v12:abc")).isEqualTo(12);
        assertThatThrownBy(() -> CryptoService.keyVersion("85073003328")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> CryptoService.keyVersion(null)).isInstanceOf(CryptoException.class);
    }
}
