package be.bstorm.formation.framework.openbao;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Accès PostgreSQL via OpenBao (ADR 0001, D5) — aucun mot de passe DB dans la configuration de l'API.
 * <ul>
 *   <li>Runtime : pool Hikari avec un utilisateur dynamique du rôle {@code app} (DML uniquement), en rotation.</li>
 *   <li>Flyway : utilisateur dynamique du rôle {@code migrator}, {@code SET ROLE formation_owner} pour que les
 *       objets appartiennent au propriétaire du schéma ; bail révoqué dès la fin des migrations.</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnBooleanProperty("app.openbao.enabled")
public class OpenBaoDataSourceConfig {

    @Bean
    RotatingDatabaseCredentials rotatingDatabaseCredentials(SecretLeaseContainer container,
                                                            OpenBaoProperties properties) {
        return new RotatingDatabaseCredentials(container, properties.databaseRole());
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties dataSourceProperties, OpenBaoReadiness readiness,
                                RotatingDatabaseCredentials credentials) {
        readiness.assertReady();
        RotatingDatabaseCredentials.Credentials initial = credentials.obtain();
        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .username(initial.username())
                .password(initial.password())
                .build();
        credentials.bind(dataSource);
        return dataSource;
    }

    /** Bail de l'utilisateur migrateur en cours (révoqué après les migrations). */
    @Bean
    MigratorLease migratorLease() {
        return new MigratorLease();
    }

    @Bean
    FlywayConfigurationCustomizer openBaoFlywayCredentials(VaultOperations vault, OpenBaoProperties properties,
                                                           DataSourceProperties dataSourceProperties,
                                                           OpenBaoReadiness readiness, MigratorLease lease) {
        return configuration -> {
            readiness.assertReady();
            VaultResponse response = vault.read("database/creds/" + properties.migratorRole());
            if (response == null || response.getData() == null) {
                throw new IllegalStateException("Aucun identifiant migrateur reçu d'OpenBao");
            }
            Map<String, Object> data = response.getData();
            lease.set(response.getLeaseId());
            configuration.dataSource(dataSourceProperties.determineUrl(),
                    (String) data.get("username"), (String) data.get("password"));
            configuration.initSql("SET ROLE " + properties.schemaOwnerRole());
            log.info("Flyway : utilisateur migrateur temporaire {} (SET ROLE {})",
                    data.get("username"), properties.schemaOwnerRole());
        };
    }

    @Bean
    FlywayMigrationStrategy revokeMigratorAfterMigration(VaultOperations vault, MigratorLease lease) {
        return flyway -> {
            try {
                flyway.migrate();
            } finally {
                String leaseId = lease.getAndClear();
                if (leaseId != null) {
                    vault.write("sys/leases/revoke", Map.of("lease_id", leaseId));
                    log.info("Flyway : bail du migrateur révoqué (utilisateur supprimé)");
                }
            }
        };
    }

    static final class MigratorLease {
        private final AtomicReference<String> leaseId = new AtomicReference<>();

        void set(String id) {
            leaseId.set(id);
        }

        String getAndClear() {
            return leaseId.getAndSet(null);
        }
    }
}
