package be.bstorm.formation.framework.openbao;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultHealth;

/**
 * État d'OpenBao dans {@code /actuator/health} (composant {@code openBao}) : DOWN s'il est scellé ou injoignable,
 * car l'API ne peut alors plus émettre de token, lire de NISS ni ouvrir de connexion DB.
 */
@RequiredArgsConstructor
public class OpenBaoHealthIndicator implements HealthIndicator {

    private final VaultOperations vaultOperations;

    @Override
    public Health health() {
        VaultHealth health = vaultOperations.opsForSys().health();
        Health.Builder builder = health.isInitialized() && !health.isSealed() ? Health.up() : Health.down();
        return builder
                .withDetail("initialized", health.isInitialized())
                .withDetail("sealed", health.isSealed())
                .withDetail("version", String.valueOf(health.getVersion()))
                .build();
    }
}
