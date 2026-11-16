package be.bstorm.formation.framework.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static be.bstorm.formation.framework.crypto.SensitiveDataType.NISS;
import static org.assertj.core.api.Assertions.assertThat;

class KeyRotationServiceTest {

    private final InMemoryCryptoService crypto = new InMemoryCryptoService();
    private final KeyRotationService rotation = new KeyRotationService(crypto, TransactionOperations.withoutTransaction());

    /** Colonne chiffrée simulée : id → chiffré (le contexte est l'id de la ligne). */
    private final Map<String, String> column = new LinkedHashMap<>();
    private final RewrapTarget target = new RewrapTarget() {
        @Override
        public String name() {
            return "test.niss";
        }

        @Override
        public SensitiveDataType type() {
            return NISS;
        }

        @Override
        public List<EncryptedValue> findOlderThan(int version, int limit) {
            return column.entrySet().stream()
                    .filter(e -> CryptoService.keyVersion(e.getValue()) < version)
                    .limit(limit)
                    .map(e -> new EncryptedValue(e.getKey(), e.getValue(), e.getKey()))
                    .toList();
        }

        @Override
        public void update(String id, String newCiphertext) {
            column.put(id, newCiphertext);
        }
    };

    @Test
    void rewrapsEveryRowInBatchesAndKeepsValues() {
        for (int i = 0; i < 7; i++) {
            column.put("row-" + i, crypto.encrypt(NISS, "value-" + i, "row-" + i));
        }
        crypto.rotate(NISS);

        long rewrapped = rotation.rewrapAll(target, 3);

        assertThat(rewrapped).isEqualTo(7);
        assertThat(column.values()).allSatisfy(c -> assertThat(CryptoService.keyVersion(c)).isEqualTo(2));
        column.forEach((id, c) -> assertThat(crypto.decrypt(NISS, c, id)).isEqualTo("value-" + id.substring(4)));

        crypto.setMinDecryptionVersion(NISS, 2);
        column.forEach((id, c) -> assertThat(crypto.decrypt(NISS, c, id)).isNotBlank());
    }

    @Test
    void nothingToDoWhenAlreadyUpToDate() {
        column.put("row-1", crypto.encrypt(NISS, "value", "row-1"));

        assertThat(rotation.rewrapAll(target, 10)).isZero();
    }
}
