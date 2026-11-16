package be.bstorm.formation.framework.openbao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Intégration réelle API ↔ OpenBao ↔ PostgreSQL, configurés par les MÊMES scripts que dev et prod
 * ({@code docker/db/roles.sh}, {@code docker/openbao/configure.sh}). L'application s'authentifie par AppRole.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenBaoIntegrationIT {

    private static final String SUPERUSER_PASSWORD = "superuser-test";
    private static final String OPENBAO_ADMIN_PASSWORD = "openbao-admin-test";

    private static final Network NETWORK = Network.newNetwork();

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("formation").withUsername("formation").withPassword(SUPERUSER_PASSWORD)
            .withNetwork(NETWORK).withNetworkAliases("db");

    static final GenericContainer<?> OPENBAO = new GenericContainer<>(DockerImageName.parse("openbao/openbao:2.7.2"))
            .withNetwork(NETWORK).withNetworkAliases("openbao")
            .withEnv("BAO_DEV_ROOT_TOKEN_ID", "root")
            .withEnv("BAO_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/openbao"), "/scripts")
            .withCommand("server", "-dev", "-dev-no-store-token", "-config=/scripts/dev.hcl")
            .withExposedPorts(8200)
            .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200));

    static final Path CREDS;

    static {
        POSTGRES.start();
        OPENBAO.start();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath("docker/db/roles.sh"), "/roles.sh");
        exec(POSTGRES, "PGHOST=localhost PGUSER=formation PGPASSWORD=" + SUPERUSER_PASSWORD
                + " PGDATABASE=formation OPENBAO_DB_ADMIN_PASSWORD=" + OPENBAO_ADMIN_PASSWORD + " sh /roles.sh");
        exec(OPENBAO, "BAO_ADDR=http://127.0.0.1:8200 BAO_TOKEN=root DB_HOST=db DB_NAME=formation"
                + " OPENBAO_DB_ADMIN_PASSWORD=" + OPENBAO_ADMIN_PASSWORD
                + " BOUND_CIDRS=0.0.0.0/0 CREDS_OUT_DIR=/tmp/creds CREDS_MODE=0444 sh /scripts/configure.sh");
        try {
            CREDS = Files.createTempDirectory("openbao-creds");
            OPENBAO.copyFileFromContainer("/tmp/creds/role-id", CREDS.resolve("role-id").toString());
            OPENBAO.copyFileFromContainer("/tmp/creds/secret-id", CREDS.resolve("secret-id").toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // Aucun mot de passe DB fourni à l'application : il doit venir d'OpenBao.
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.openbao.enabled", () -> "true");
        registry.add("app.openbao.uri", () -> "http://" + OPENBAO.getHost() + ":" + OPENBAO.getMappedPort(8200));
        registry.add("app.openbao.role-id-file", () -> CREDS.resolve("role-id").toString());
        registry.add("app.openbao.secret-id-file", () -> CREDS.resolve("secret-id").toString());
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RotatingDatabaseCredentials credentials;
    @Autowired
    OpenBaoHealthIndicator healthIndicator;
    @Autowired
    VaultOperations vault;

    @Test
    void runtimeUsesDynamicUserWithDmlOnly() {
        String user = currentUser();

        assertThat(user).startsWith("v-approle-app-");
        assertThat(jdbc.queryForObject("select pg_has_role(current_user, 'formation_rw', 'member')", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("select pg_has_role(current_user, 'formation_owner', 'member')", Boolean.class))
                .isFalse();
        assertThatThrownBy(() -> jdbc.execute("create table should_fail(id int)"))
                .rootCause().hasMessageContaining("permission denied for schema public");
    }

    @Test
    void flywayRanAsSchemaOwnerAndMigratorWasRevoked() {
        assertThat(jdbc.queryForObject(
                "select tableowner from pg_tables where tablename = 'flyway_schema_history'", String.class))
                .isEqualTo("formation_owner");
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_roles where rolname like 'v-approle-migrator-%'", Integer.class))
                .isZero();
    }

    @Test
    void rotationSwitchesDatabaseUserWithoutRestart() {
        String before = currentUser();

        credentials.rotateNow();

        String after = currentUser();
        assertThat(after).startsWith("v-approle-app-").isNotEqualTo(before);
        assertThat(credentials.current().username()).isEqualTo(after);
    }

    @Test
    void healthIndicatorReportsUnsealedOpenBao() {
        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void transitIsAvailableToTheApplication() {
        String plaintext = Base64.getEncoder().encodeToString("85073003328".getBytes());

        VaultResponse encrypted = vault.write("transit/encrypt/niss", Map.of("plaintext", plaintext));

        assertThat(encrypted).isNotNull();
        assertThat((String) encrypted.getRequiredData().get("ciphertext")).startsWith("vault:v1:");
    }

    private String currentUser() {
        return jdbc.queryForObject("select current_user", String.class);
    }

    private static void exec(GenericContainer<?> container, String command) {
        try {
            ExecResult result = container.execInContainer("sh", "-c", command);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Échec (" + result.getExitCode() + ") : " + command
                        + "\n" + result.getStdout() + "\n" + result.getStderr());
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
