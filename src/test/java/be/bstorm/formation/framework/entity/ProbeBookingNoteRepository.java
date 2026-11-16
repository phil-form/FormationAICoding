package be.bstorm.formation.framework.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProbeBookingNoteRepository extends JpaRepository<ProbeBookingNote, UUID> {
}
