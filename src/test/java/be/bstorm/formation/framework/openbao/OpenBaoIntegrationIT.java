package be.bstorm.formation.framework.openbao;

import be.bstorm.formation.framework.crypto.CryptoService;
import be.bstorm.formation.framework.crypto.SensitiveDataType;
import be.bstorm.formation.framework.crypto.TransitCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Intégration réelle API ↔ OpenBao ↔ PostgreSQL ({@link OpenBaoTestEnvironment} : mêmes scripts que dev et prod).
 * L'application s'authentifie par AppRole.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenBaoIntegrationIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OpenBaoTestEnvironment.POSTGRES::getJdbcUrl);
        // Aucun mot de passe DB fourni à l'application : il doit venir d'OpenBao.
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.openbao.enabled", () -> "true");
        registry.add("app.openbao.uri", () -> OpenBaoTestEnvironment.uri().toString());
        registry.add("app.openbao.role-id-file", () -> OpenBaoTestEnvironment.CREDS.resolve("role-id").toString());
        registry.add("app.openbao.secret-id-file", () -> OpenBaoTestEnvironment.CREDS.resolve("secret-id").toString());
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RotatingDatabaseCredentials credentials;
    @Autowired
    OpenBaoHealthIndicator healthIndicator;

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
    void btreeGistIsInstalledByRolesScriptAndUsableByMigrations() {
        // Créée par docker/db/roles.sh (superutilisateur) ; la sonde de test probe_booking l'utilise (EXCLUDE).
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'btree_gist'", Integer.class))
                .isOne();
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname = 'probe_booking_no_overlap'",
                Integer.class)).isOne();
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
    void cryptoServiceUsesOpenBaoTransit(@Autowired CryptoService crypto) {
        String ciphertext = crypto.encrypt(SensitiveDataType.NISS, "85073003328", "row-1");

        assertThat(crypto).isInstanceOf(TransitCryptoService.class);
        assertThat(ciphertext).startsWith("vault:v");
        assertThat(crypto.decrypt(SensitiveDataType.NISS, ciphertext, "row-1")).isEqualTo("85073003328");
    }

    private String currentUser() {
        return jdbc.queryForObject("select current_user", String.class);
    }
}
