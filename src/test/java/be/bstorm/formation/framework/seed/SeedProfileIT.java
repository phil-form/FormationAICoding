package be.bstorm.formation.framework.seed;

import be.bstorm.formation.TestcontainersConfig;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Profil seed sur PostgreSQL réel : exécution au démarrage, idempotence, une transaction par seeder.
 * Le mot de passe de démonstration est fourni : sans lui, {@code UserSeeder} (feature 001, RG-18) fait échouer le
 * démarrage en profil seed.
 */
@SpringBootTest(properties = "app.seed.demo-password=Seed-Profile-Demo-2026")
@ActiveProfiles({"test", SeedRunner.PROFILE})
@Import({TestcontainersConfig.class, SeedTestSeeders.Table.class, SeedTestSeeders.Rows.class})
class SeedProfileIT {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SeedRunner runner;
    @Autowired
    List<DataSeeder> seeders;
    @Autowired
    TransactionOperations transactions;

    @BeforeEach
    void cleanFailedRows() {
        jdbc.update("delete from seed_probe where label like 'rollback-%'");
    }

    @Test
    void seedersRanAtStartupInOrder() {
        assertThat(jdbc.queryForList("select label from seed_probe order by id", String.class))
                .containsExactly("row-1", "row-2");
    }

    @Test
    void fakerIsAvailableAndDeterministic(@Autowired Faker faker) {
        Faker sameSeed = new SeedFakerConfig().seedFaker(42);

        assertThat(faker.name().fullName()).isNotBlank();
        assertThat(new SeedFakerConfig().seedFaker(42).name().fullName()).isEqualTo(sameSeed.name().fullName());
    }

    @Test
    void rerunIsIdempotent() {
        runner.run(null);

        assertThat(jdbc.queryForObject("select count(*) from seed_probe", Integer.class)).isEqualTo(2);
    }

    @Test
    void failingSeederRollsBackItsOwnWritesOnly() {
        DataSeeder failing = new DataSeeder() {
            @Override
            public boolean shouldRun() {
                return true;
            }

            @Override
            public void seed() {
                jdbc.update("insert into seed_probe(label) values ('rollback-me')");
                throw new IllegalStateException("échec simulé");
            }
        };
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test", SeedRunner.PROFILE);
        SeedRunner withFailure = new SeedRunner(List.of(failing), transactions, env);

        assertThatThrownBy(() -> withFailure.run(null)).hasMessageContaining("échec simulé");
        assertThat(jdbc.queryForObject("select count(*) from seed_probe where label = 'rollback-me'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from seed_probe", Integer.class)).isEqualTo(2);
    }
}
