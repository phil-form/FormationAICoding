package be.bstorm.formation.framework.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Comparator;
import java.util.List;

/**
 * Exécute les {@link DataSeeder} au démarrage (après Flyway), avec le profil {@code seed} uniquement.
 * <ul>
 *   <li>ordre : {@link DataSeeder#order()} puis nom ;</li>
 *   <li>une transaction par seeder ; {@link DataSeeder#shouldRun()} évalué dans cette transaction ;</li>
 *   <li>une erreur fait échouer le démarrage (pas de base à moitié peuplée en silence) ;</li>
 *   <li>refus catégorique si le profil {@code prod} est aussi actif.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile(SeedRunner.PROFILE)
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    public static final String PROFILE = "seed";

    private final List<DataSeeder> seeders;
    private final TransactionOperations transactions;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException("Le profil 'seed' est interdit avec le profil 'prod'.");
        }
        List<DataSeeder> ordered = seeders.stream()
                .sorted(Comparator.comparingInt(DataSeeder::order).thenComparing(DataSeeder::name))
                .toList();
        log.info("Seed : {} seeder(s) à évaluer", ordered.size());

        int ran = 0;
        for (DataSeeder seeder : ordered) {
            long start = System.nanoTime();
            Boolean executed = transactions.execute(status -> {
                if (!seeder.shouldRun()) {
                    return false;
                }
                seeder.seed();
                return true;
            });
            if (Boolean.TRUE.equals(executed)) {
                ran++;
                log.info("Seed : {} exécuté en {} ms", seeder.name(), (System.nanoTime() - start) / 1_000_000);
            } else {
                log.info("Seed : {} ignoré (données déjà présentes)", seeder.name());
            }
        }
        log.info("Seed terminé : {} exécuté(s), {} ignoré(s)", ran, ordered.size() - ran);
    }
}
