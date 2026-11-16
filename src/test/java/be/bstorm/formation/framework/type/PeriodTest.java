package be.bstorm.formation.framework.type;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodTest {

    private static Instant at(String time) {
        return Instant.parse("2026-11-16T" + time + ":00Z");
    }

    private static Period period(String start, String end) {
        return Period.of(at(start), at(end));
    }

    @Test
    void acceptsWholeHoursWithEndAfterStart() {
        Period period = period("09:00", "12:00");

        assertThat(period.hours()).isEqualTo(3);
        assertThat(period.start()).isEqualTo(at("09:00"));
    }

    @Test
    void rejectsMissingReversedEmptyOrNonWholeHourBounds() {
        assertThatThrownBy(() -> Period.of(null, at("10:00"))).isInstanceOf(InvalidPeriodException.class);
        assertThatThrownBy(() -> period("10:00", "09:00")).isInstanceOf(InvalidPeriodException.class)
                .hasMessage("End must be after start.");
        assertThatThrownBy(() -> period("10:00", "10:00")).isInstanceOf(InvalidPeriodException.class);
        assertThatThrownBy(() -> period("09:30", "11:00")).isInstanceOf(InvalidPeriodException.class)
                .hasMessage("Start and end must be on whole hours.");
        assertThatThrownBy(() -> Period.of(at("09:00"), Instant.parse("2026-11-16T11:00:01Z")))
                .isInstanceOf(InvalidPeriodException.class);
    }

    @Test
    void invalidPeriodIsABadRequest() {
        assertThatThrownBy(() -> period("10:00", "09:00"))
                .isInstanceOfSatisfying(InvalidPeriodException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getTitle()).isEqualTo("Invalid Period");
                });
    }

    @Test
    void adjacentPeriodsDoNotOverlap() {
        assertThat(period("09:00", "11:00").overlaps(period("11:00", "13:00"))).isFalse();
        assertThat(period("11:00", "13:00").overlaps(period("09:00", "11:00"))).isFalse();
    }

    @Test
    void overlappingAndNestedPeriodsOverlap() {
        assertThat(period("09:00", "12:00").overlaps(period("11:00", "13:00"))).isTrue();
        assertThat(period("09:00", "17:00").overlaps(period("10:00", "11:00"))).isTrue();
        assertThat(period("10:00", "11:00").overlaps(period("09:00", "17:00"))).isTrue();
    }

    @Test
    void containsIsHalfOpen() {
        Period period = period("09:00", "11:00");

        assertThat(period.contains(at("09:00"))).isTrue();
        assertThat(period.contains(Instant.parse("2026-11-16T10:59:59Z"))).isTrue();
        assertThat(period.contains(at("11:00"))).isFalse();
        assertThat(period.contains(at("08:00"))).isFalse();
    }

    @Test
    void intersection() {
        assertThat(period("09:00", "12:00").intersection(period("11:00", "13:00"))).contains(period("11:00", "12:00"));
        assertThat(period("09:00", "11:00").intersection(period("11:00", "13:00"))).isEmpty();
    }

    @Test
    void wholeHourInUtcIsWholeHourInBrussels() {
        // Changement d'heure (29/03/2026, 02:00 → 03:00 à Bruxelles) : les heures pleines restent pleines.
        assertThat(Period.isWholeHour(Instant.parse("2026-03-29T01:00:00Z"))).isTrue();
        assertThat(Period.isWholeHour(Instant.parse("2026-03-29T01:00:00.001Z"))).isFalse();
    }
}
