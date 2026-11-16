package be.bstorm.formation.framework.seed;

import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Locale;
import java.util.Random;

/**
 * Générateur de données réalistes pour les seeders (profil {@code seed} uniquement).
 * <p>
 * Déterministe : même graine → mêmes données à chaque {@code ./start.sh --seed} (bugs reproductibles).
 * Graine modifiable par {@code app.seed.random-seed}. Datafaker est exclu du jar de production : cette classe n'y
 * est jamais chargée (profil inactif → seules ses métadonnées sont lues).
 */
@Configuration(proxyBeanMethods = false)
@Profile(SeedRunner.PROFILE)
public class SeedFakerConfig {

    @Bean
    Faker seedFaker(@Value("${app.seed.random-seed:42}") long randomSeed) {
        return new Faker(Locale.FRENCH, new Random(randomSeed));
    }
}
