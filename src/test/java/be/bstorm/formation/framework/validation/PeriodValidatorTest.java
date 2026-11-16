package be.bstorm.formation.framework.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodValidatorTest {

    @ValidPeriod
    record Request(@NotNull Instant start, @NotNull Instant end) implements PeriodRequest {
    }

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private static Map<String, String> violations(String start, String end) {
        Set<ConstraintViolation<Request>> violations = VALIDATOR.validate(new Request(
                start == null ? null : Instant.parse(start), end == null ? null : Instant.parse(end)));
        return violations.stream().collect(Collectors.toMap(v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage, (a, b) -> a + " | " + b));
    }

    @Test
    void validPeriod() {
        assertThat(violations("2026-11-16T09:00:00Z", "2026-11-16T10:00:00Z")).isEmpty();
    }

    @Test
    void endBeforeStartIsReportedOnEnd() {
        assertThat(violations("2026-11-16T10:00:00Z", "2026-11-16T09:00:00Z"))
                .containsExactly(Map.entry("end", "must be after start"));
    }

    @Test
    void nonWholeHoursAreReportedOnTheirField() {
        assertThat(violations("2026-11-16T09:30:00Z", "2026-11-16T11:15:00Z"))
                .containsOnlyKeys("start", "end")
                .containsEntry("start", "must be on a whole hour");
    }

    @Test
    void missingBoundsAreLeftToNotNull() {
        assertThat(violations(null, "2026-11-16T09:00:00Z")).containsOnlyKeys("start");
    }
}
