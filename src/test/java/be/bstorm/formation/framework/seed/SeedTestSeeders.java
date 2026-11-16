package be.bstorm.formation.framework.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeders de test (le domaine n'a pas encore d'entité) : une table technique puis deux lignes.
 * Annotés {@link Seeder} : ils ne doivent exister qu'avec le profil seed.
 */
public final class SeedTestSeeders {

    private SeedTestSeeders() {
    }

    @Seeder
    @RequiredArgsConstructor
    public static class Table implements DataSeeder {
        private final JdbcTemplate jdbc;

        @Override
        public int order() {
            return 1;
        }

        @Override
        public boolean shouldRun() {
            return jdbc.queryForObject("select to_regclass('public.seed_probe') is null", Boolean.class);
        }

        @Override
        public void seed() {
            jdbc.execute("create table seed_probe(id serial primary key, label text not null)");
        }
    }

    @Seeder
    @RequiredArgsConstructor
    public static class Rows implements DataSeeder {
        private final JdbcTemplate jdbc;

        @Override
        public int order() {
            return 2;
        }

        @Override
        public boolean shouldRun() {
            return jdbc.queryForObject("select count(*) from seed_probe", Integer.class) == 0;
        }

        @Override
        public void seed() {
            jdbc.update("insert into seed_probe(label) values ('row-1'), ('row-2')");
        }
    }
}
