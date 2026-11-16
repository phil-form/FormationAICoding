package be.bstorm.formation.framework.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;

/**
 * Suite d'une rotation de clé : re-chiffre (rewrap) toutes les valeurs d'une colonne avec la dernière version, par
 * lots transactionnels, sans que la valeur en clair ne transite par l'API.
 * <p>
 * Procédure complète (docs/ops/OPENBAO.md) : 1. rotation de la clé (admin) ; 2. {@link #rewrapAll} pour chaque
 * colonne ; 3. relever {@code min_decryption_version} (admin) — les anciennes versions deviennent inutilisables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeyRotationService {

    static final int DEFAULT_BATCH_SIZE = 500;

    private final CryptoService cryptoService;
    private final TransactionOperations transactions;

    public long rewrapAll(RewrapTarget target) {
        return rewrapAll(target, DEFAULT_BATCH_SIZE);
    }

    /** @return nombre de valeurs re-chiffrées */
    public long rewrapAll(RewrapTarget target, int batchSize) {
        int latest = cryptoService.latestKeyVersion(target.type());
        long total = 0;
        while (true) {
            Integer done = transactions.execute(status -> {
                List<RewrapTarget.EncryptedValue> batch = target.findOlderThan(latest, batchSize);
                for (RewrapTarget.EncryptedValue value : batch) {
                    String rewrapped = cryptoService.rewrap(target.type(), value.ciphertext(), value.context());
                    if (CryptoService.keyVersion(rewrapped) < latest) {
                        throw new CryptoException("Rewrap sans effet (" + target.name() + ", ligne " + value.id() + ")");
                    }
                    target.update(value.id(), rewrapped);
                }
                return batch.size();
            });
            if (done == null || done == 0) {
                break;
            }
            total += done;
            log.info("Rewrap {} : {} valeur(s) passée(s) en v{} (total {})", target.name(), done, latest, total);
        }
        log.info("Rewrap {} terminé : {} valeur(s) en v{}", target.name(), total, latest);
        return total;
    }
}
