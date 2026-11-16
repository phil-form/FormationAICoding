package be.bstorm.formation.framework.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Sonde : entité non supprimable qui référence une {@link ProbeBooking} (historique d'un élément supprimé). */
@Getter
@Entity
@Table(name = "probe_booking_note")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProbeBookingNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private ProbeBooking booking;

    @Column(nullable = false, length = 100)
    private String text;

    public ProbeBookingNote(ProbeBooking booking, String text) {
        this.booking = booking;
        this.text = text;
    }
}
