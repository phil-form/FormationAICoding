package be.bstorm.formation.framework.openbao;

import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * PostgreSQL 18 + OpenBao 2.7 réels, configurés par les MÊMES scripts que dev et prod ({@code docker/db/roles.sh},
 * {@code docker/openbao/configure.sh}). Démarré une seule fois par JVM et partagé par les tests d'intégration.
 */
public final class OpenBaoTestEnvironment {

    static final String ROOT_TOKEN = "root";
    private static final String SUPERUSER_PASSWORD = "superuser-test";
    private static final String OPENBAO_ADMIN_PASSWORD = "openbao-admin-test";

    private static final Network NETWORK = Network.newNetwork();

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("formation").withUsername("formation").withPassword(SUPERUSER_PASSWORD)
            .withNetwork(NETWORK).withNetworkAliases("db");

    public static final GenericContainer<?> OPENBAO = new GenericContainer<>(DockerImageName.parse("openbao/openbao:2.7.2"))
            .withNetwork(NETWORK).withNetworkAliases("openbao")
            .withEnv("BAO_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
            .withEnv("BAO_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/openbao"), "/scripts")
            .withCommand("server", "-dev", "-dev-no-store-token", "-config=/scripts/dev.hcl")
            .withExposedPorts(8200)
            .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200));

    /** Dossier contenant {@code role-id} et {@code secret-id} de l'AppRole {@code api}. */
    public static final Path CREDS;

    static {
        POSTGRES.start();
        OPENBAO.start();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath("docker/db/roles.sh"), "/roles.sh");
        exec(POSTGRES, "PGHOST=localhost PGUSER=formation PGPASSWORD=" + SUPERUSER_PASSWORD
                + " PGDATABASE=formation OPENBAO_DB_ADMIN_PASSWORD=" + OPENBAO_ADMIN_PASSWORD + " sh /roles.sh");
        exec(OPENBAO, "BAO_ADDR=http://127.0.0.1:8200 BAO_TOKEN=" + ROOT_TOKEN + " DB_HOST=db DB_NAME=formation"
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

    private OpenBaoTestEnvironment() {
    }

    public static URI uri() {
        return URI.create("http://" + OPENBAO.getHost() + ":" + OPENBAO.getMappedPort(8200));
    }

    /** Client administrateur (token root du mode dev) : rotations, configuration de clés. */
    public static VaultTemplate admin() {
        return new VaultTemplate(VaultEndpoint.from(uri()), new TokenAuthentication(ROOT_TOKEN));
    }

    /** Client avec un token de l'AppRole {@code api} : exactement les droits de l'application. */
    public static VaultTemplate api() {
        VaultResponse login = admin().write("auth/approle/login", Map.of(
                "role_id", read("role-id"), "secret_id", read("secret-id")));
        if (login == null || login.getAuth() == null) {
            throw new IllegalStateException("Connexion AppRole impossible");
        }
        return new VaultTemplate(VaultEndpoint.from(uri()),
                new TokenAuthentication((String) login.getAuth().get("client_token")));
    }

    private static String read(String file) {
        try {
            return Files.readString(CREDS.resolve(file)).strip();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
