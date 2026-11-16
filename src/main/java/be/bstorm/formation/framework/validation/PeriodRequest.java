package be.bstorm.formation.framework.validation;

import java.time.Instant;

/**
 * DTO portant une période ({@code start}, {@code end}) : l'implémenter et annoter le record {@link ValidPeriod}.
 * Les champs restent annotés {@code @NotNull} ; « pas dans le passé » se vérifie dans le service (horloge injectée).
 */
public interface PeriodRequest {

    Instant start();

    Instant end();
}
