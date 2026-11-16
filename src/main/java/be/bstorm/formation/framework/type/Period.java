package be.bstorm.formation.framework.type;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Période {@code [start, end[} en UTC, sur des <b>heures pleines</b> (réservations « à l'heure près »), {@code end}
 * strictement après {@code start}. Intervalle semi-ouvert : deux périodes jointives ({@code 9h–11h} et
 * {@code 11h–13h}) ne se chevauchent pas — même sémantique que {@code tstzrange(start_at, end_at)} en base (RM1).
 * <p>
 * Embarquable dans une entité : colonnes {@code start_at} et {@code end_at} ({@code timestamptz not null}).
 */
@Embeddable
public record Period(
        @Column(name = "start_at", nullable = false) Instant start,
        @Column(name = "end_at", nullable = false) Instant end) {

    public Period {
        if (start == null || end == null) {
            throw new InvalidPeriodException("Start and end are required.");
        }
        if (!end.isAfter(start)) {
            throw new InvalidPeriodException("End must be after start.");
        }
        if (!isWholeHour(start) || !isWholeHour(end)) {
            throw new InvalidPeriodException("Start and end must be on whole hours.");
        }
    }

    public static Period of(Instant start, Instant end) {
        return new Period(start, end);
    }

    /** Heure pleine : minutes, secondes et fractions à zéro. */
    public static boolean isWholeHour(Instant instant) {
        return instant.truncatedTo(ChronoUnit.HOURS).equals(instant);
    }

    public boolean overlaps(Period other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public long hours() {
        return duration().toHours();
    }

    public Optional<Period> intersection(Period other) {
        if (!overlaps(other)) {
            return Optional.empty();
        }
        Instant from = start.isAfter(other.start) ? start : other.start;
        Instant to = end.isBefore(other.end) ? end : other.end;
        return Optional.of(new Period(from, to));
    }
}
