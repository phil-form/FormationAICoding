package be.bstorm.formation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class FormationApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoadsAndFlywayMigratesPostgres18() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);

        String version = jdbc.queryForObject("show server_version", String.class);
        assertThat(version).startsWith("18.");
    }
}
