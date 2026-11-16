package be.bstorm.formation.framework.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProbeBookingRepository extends JpaRepository<ProbeBooking, UUID>, JpaSpecificationExecutor<ProbeBooking> {

    @Query("select b from ProbeBooking b where b.code like :prefix%")
    List<ProbeBooking> findByJpqlCodePrefix(String prefix);
}
