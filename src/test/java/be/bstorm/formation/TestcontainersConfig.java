package be.bstorm.formation;

import be.bstorm.formation.framework.crypto.CryptoService;
import be.bstorm.formation.framework.crypto.InMemoryCryptoService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 18 jetable pour les tests d'intégration : {@code @Import(TestcontainersConfig.class)}.
 * Fournit aussi un {@link CryptoService} en mémoire (même contrat que Transit) : les tests sans OpenBao n'ont pas de
 * coffre — seul {@code OpenBaoIntegrationIT} utilise la vraie implémentation.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Extensions créées par docker/db/roles.sh en dev / prod (superutilisateur) : même chose ici.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
                .withInitScript("db/test-init.sql");
    }

    @Bean
    CryptoService inMemoryCryptoService() {
        return new InMemoryCryptoService();
    }
}
