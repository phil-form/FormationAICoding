package be.bstorm.formation.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Temps (réponse client C12) : tout est stocké et échangé en UTC ({@code Instant} / {@code timestamptz}) ; le fuseau
 * métier ne sert qu'aux calculs calendaires (jours, heures ouvrées) et à l'affichage côté front.
 * <p>
 * Injecter {@link Clock} plutôt qu'appeler {@code Instant.now()} : les tests figent l'heure avec un {@code Clock}
 * {@code @Primary}.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /** Fuseau métier unique (Belgique). Ses décalages sont des heures entières : heure pleine UTC = heure pleine locale. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Brussels");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
