package be.bstorm.formation.framework.entity;

import be.bstorm.formation.TestcontainersConfig;
import be.bstorm.formation.framework.security.WithCurrentUser;
import be.bstorm.formation.framework.type.Period;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suppression logique (RM10) sur une vraie base : invisibilité dans toutes les requêtes JPA, ligne conservée,
 * unicité partielle, et contrainte d'exclusion btree_gist (modèle de RM1).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SoftDeleteIT {

    private static final String USER_ID = "0193a5f2-0000-7000-8000-0000000000aa";
    private static final Instant MONDAY_9 = Instant.parse("2026-11-16T09:00:00Z");

    @Autowired
    ProbeBookingRepository bookings;

    @Autowired
    ProbeBookingNoteRepository notes;

    @Autowired
    SoftDeleter softDeleter;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    private static String code() {
        return "T-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Period hours(int from, int to) {
        return Period.of(MONDAY_9.plusSeconds(3600L * (from - 9)), MONDAY_9.plusSeconds(3600L * (to - 9)));
    }

    private ProbeBooking create(UUID depot, String code, Period period) {
        return tx.execute(s -> bookings.save(new ProbeBooking(UUID.randomUUID(), depot, code, period)));
    }

    private void delete(UUID id) {
        tx.executeWithoutResult(s -> softDeleter.delete(bookings.findById(id).orElseThrow()));
    }

    @Test
    @WithCurrentUser(id = USER_ID)
    void deletedRowIsInvisibleToJpaButStaysInDatabaseWithAuthor() {
        String code = code();
        UUID id = create(UUID.randomUUID(), code, hours(9, 10)).getId();

        delete(id);

        tx.executeWithoutResult(s -> {
            assertThat(bookings.findById(id)).isEmpty();
            assertThat(bookings.existsById(id)).isFalse();
            assertThat(bookings.findAll()).extracting(ProbeBooking::getId).doesNotContain(id);
            assertThat(bookings.findByJpqlCodePrefix(code)).isEmpty();
            assertThat(bookings.findAll((root, q, cb) -> cb.equal(root.get("code"), code))).isEmpty();
        });
        Map<String, Object> row = jdbc.sql("SELECT deleted_at, deleted_by FROM probe_booking WHERE id = :id")
                .param("id", id).query().singleRow();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deleted_by")).isEqualTo(UUID.fromString(USER_ID));
    }

    @Test
    void systemDeletionHasNoAuthor() {
        UUID id = create(UUID.randomUUID(), code(), hours(9, 10)).getId();

        delete(id);

        Map<String, Object> row = jdbc.sql("SELECT deleted_at, deleted_by FROM probe_booking WHERE id = :id")
                .param("id", id).query().singleRow();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deleted_by")).isNull();
    }

    @Test
    void secondDeletionKeepsFirstDateAndAuthor() {
        ProbeBooking booking = new ProbeBooking(UUID.randomUUID(), UUID.randomUUID(), code(), hours(9, 10));
        UUID first = UUID.randomUUID();
        booking.markDeleted(first, MONDAY_9);
        booking.markDeleted(UUID.randomUUID(), MONDAY_9.plusSeconds(60));

        assertThat(booking.isDeleted()).isTrue();
        assertThat(booking.getDeletedAt()).isEqualTo(MONDAY_9);
        assertThat(booking.getDeletedBy()).isEqualTo(first);
    }

    @Test
    void deletedUniqueValueCanBeReusedButActiveDuplicateIsRefused() {
        String code = code();
        UUID id = create(UUID.randomUUID(), code, hours(9, 10)).getId();

        assertThatThrownBy(() -> create(UUID.randomUUID(), code, hours(9, 10)))
                .isInstanceOf(DataIntegrityViolationException.class);

        delete(id);
        assertThat(create(UUID.randomUUID(), code, hours(9, 10)).getId()).isNotEqualTo(id);
    }

    @Test
    void preassignedIdIsKeptAtPersistence() {
        ProbeBooking booking = new ProbeBooking(UUID.randomUUID(), UUID.randomUUID(), code(), hours(9, 10));
        UUID id = booking.assignId();

        assertThat(booking.assignId()).isEqualTo(id);
        assertThat(id.version()).isEqualTo(7);
        assertThat(tx.execute(s -> bookings.save(booking)).getId()).isEqualTo(id);
        assertThat(bookings.existsById(id)).isTrue();
    }

    @Test
    void generatedIdsAreUuidV7() {
        assertThat(create(UUID.randomUUID(), code(), hours(9, 10)).getId().version()).isEqualTo(7);
    }

    @Test
    void periodIsStoredInStartAndEndColumns() {
        UUID id = create(UUID.randomUUID(), code(), hours(9, 12)).getId();

        Period stored = tx.execute(s -> bookings.findById(id).orElseThrow().getPeriod());
        assertThat(stored).isEqualTo(hours(9, 12));
        assertThat(jdbc.sql("SELECT end_at - start_at = interval '3 hours' FROM probe_booking WHERE id = :id")
                .param("id", id).query(Boolean.class).single()).isTrue();
    }

    @Test
    void exclusionConstraintRefusesOverlapsOnSameResourceOnly() {
        UUID depot = UUID.randomUUID();
        UUID first = create(depot, code(), hours(9, 12)).getId();

        assertThatThrownBy(() -> create(depot, code(), hours(11, 13)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> create(depot, code(), hours(10, 11)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(create(depot, code(), hours(12, 14))).isNotNull();             // jointive : acceptée
        assertThat(create(UUID.randomUUID(), code(), hours(9, 12))).isNotNull();  // autre ressource

        delete(first);
        assertThat(create(depot, code(), hours(10, 11))).isNotNull();             // la supprimée ne bloque plus
    }

    /**
     * Comportement Hibernate à connaître : une association vers un élément supprimé garde sa clé étrangère et son
     * identifiant, mais l'élément n'est plus chargeable (la restriction s'applique aussi au chargement par
     * association). Un historique qui doit afficher l'élément supprimé recopie les données utiles.
     */
    @Test
    void historyKeepsTheIdOfADeletedEntityButCannotLoadIt() {
        ProbeBooking booking = create(UUID.randomUUID(), code(), hours(9, 10));
        UUID noteId = tx.execute(s -> notes.save(new ProbeBookingNote(bookings.getReferenceById(booking.getId()),
                "history")).getId());

        delete(booking.getId());

        tx.executeWithoutResult(s -> {
            ProbeBookingNote note = notes.findById(noteId).orElseThrow();
            assertThat(note.getBooking().getId()).isEqualTo(booking.getId());
            assertThat(Hibernate.isInitialized(note.getBooking())).isFalse();
            assertThatThrownBy(() -> note.getBooking().getCode()).isInstanceOf(EntityNotFoundException.class);
        });
    }
}
