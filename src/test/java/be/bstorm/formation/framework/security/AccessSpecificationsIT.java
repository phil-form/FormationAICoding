package be.bstorm.formation.framework.security;

import be.bstorm.formation.TestcontainersConfig;
import be.bstorm.formation.framework.entity.ProbeBooking;
import be.bstorm.formation.framework.entity.ProbeBookingRepository;
import be.bstorm.formation.framework.type.Period;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cloisonnement RM9 appliqué DANS la requête (listes, pagination), sur une vraie base. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessSpecificationsIT {

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID depotA = UUID.randomUUID();
    private final UUID depotB = UUID.randomUUID();

    @Autowired
    ProbeBookingRepository bookings;

    @BeforeAll
    void data() {
        Instant base = Instant.parse("2026-11-16T08:00:00Z");
        int hour = 0;
        for (UUID depot : List.of(depotA, depotB)) {
            for (UUID owner : List.of(me, other)) {
                Instant start = base.plusSeconds(3600L * hour++);
                bookings.save(new ProbeBooking(owner, depot, (depot == depotA ? "A-" : "B-") + (owner == me ? "ME" : "OTHER")
                        + "-" + depot.toString().substring(0, 6), Period.of(start, start.plusSeconds(3600))));
            }
        }
    }

    private CurrentUser user(UUID id, UUID depot, Role... roles) {
        return new CurrentUser(id, EnumSet.of(roles[0], roles), depot);
    }

    /** Limite le résultat aux données de ce test (la base est partagée). */
    private Specification<ProbeBooking> onlyThisTest() {
        return (root, query, cb) -> root.get("depotId").in(depotA, depotB);
    }

    private List<String> owned(CurrentUser user) {
        return bookings.findAll(AccessSpecifications.<ProbeBooking>ownedOrManaged(user, "ownerId", "depotId")
                .and(onlyThisTest())).stream().map(b -> b.getCode().substring(0, b.getCode().lastIndexOf('-'))).sorted().toList();
    }

    private List<String> managed(CurrentUser user) {
        return bookings.findAll(AccessSpecifications.<ProbeBooking>managed(user, "depotId").and(onlyThisTest()))
                .stream().map(b -> b.getCode().substring(0, b.getCode().lastIndexOf('-'))).sorted().toList();
    }

    @Test
    void driverSeesOnlyOwnRowsInAllDepots() {
        assertThat(owned(user(me, depotA, Role.DRIVER))).containsExactly("A-ME", "B-ME");
    }

    @Test
    void managerSeesTheirDepotOnly() {
        assertThat(owned(user(UUID.randomUUID(), depotA, Role.FLEET_MANAGER))).containsExactly("A-ME", "A-OTHER");
    }

    @Test
    void managerWhoDrivesAlsoSeesOwnRowsElsewhere() {
        assertThat(owned(user(me, depotA, Role.FLEET_MANAGER, Role.DRIVER))).containsExactly("A-ME", "A-OTHER", "B-ME");
    }

    @Test
    void adminSeesEverything() {
        assertThat(owned(user(UUID.randomUUID(), null, Role.ADMIN))).hasSize(4);
        assertThat(managed(user(UUID.randomUUID(), null, Role.ADMIN))).hasSize(4);
    }

    @Test
    void managedResourcesAreHiddenFromDrivers() {
        assertThat(managed(user(me, depotA, Role.DRIVER))).isEmpty();
        assertThat(managed(user(me, depotB, Role.FLEET_MANAGER))).containsExactly("B-ME", "B-OTHER");
    }

    @Test
    void paginationCountsOnlyAccessibleRows() {
        var page = bookings.findAll(AccessSpecifications.<ProbeBooking>ownedOrManaged(user(me, depotA, Role.DRIVER),
                "ownerId", "depotId").and(onlyThisTest()), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }
}
