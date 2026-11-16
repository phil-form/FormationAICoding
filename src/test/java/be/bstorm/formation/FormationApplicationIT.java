package be.bstorm.formation;

import be.bstorm.formation.framework.seed.DataSeeder;
import be.bstorm.formation.framework.seed.SeedRunner;
import be.bstorm.formation.framework.seed.SeedTestSeeders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, SeedTestSeeders.Table.class, SeedTestSeeders.Rows.class})
class FormationApplicationIT {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ApplicationContext context;

    @Test
    void seedersDoNotExistWithoutSeedProfile() {
        assertThat(context.getBeansOfType(DataSeeder.class)).isEmpty();
        assertThat(context.getBeansOfType(SeedRunner.class)).isEmpty();
        assertThat(jdbc.queryForObject("select to_regclass('public.seed_probe') is null", Boolean.class)).isTrue();
    }

    @Test
    void contextLoadsAndFlywayMigratesPostgres18() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);

        String version = jdbc.queryForObject("show server_version", String.class);
        assertThat(version).startsWith("18.");
    }
}
