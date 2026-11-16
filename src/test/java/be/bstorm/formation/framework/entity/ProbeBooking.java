package be.bstorm.formation.framework.entity;

import be.bstorm.formation.framework.type.Period;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/** Entité de sonde (tests du socle uniquement) : suppression logique, cloisonnement, période (table de test). */
@Getter
@Setter
@Entity
@Table(name = "probe_booking")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProbeBooking extends SoftDeletableEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "depot_id", nullable = false)
    private UUID depotId;

    @Column(nullable = false, length = 20)
    private String code;

    @Embedded
    private Period period;

    public ProbeBooking(UUID ownerId, UUID depotId, String code, Period period) {
        this.ownerId = ownerId;
        this.depotId = depotId;
        this.code = code;
        this.period = period;
    }
}
