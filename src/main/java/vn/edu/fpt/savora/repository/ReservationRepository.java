package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.savora.entity.Reservation;
import vn.edu.fpt.savora.enums.ReservationStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    List<Reservation> findByReservationDateAndStatusIn(LocalDate reservationDate, Collection<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.diningTable.tableId = :tableId AND r.reservationDate = :date AND r.status IN :statuses")
    List<Reservation> findActiveByTableAndDate(
            @Param("tableId") Integer tableId,
            @Param("date") LocalDate date,
            @Param("statuses") Collection<ReservationStatus> statuses
    );

    List<Reservation> findByCustomer_UserIdOrderByCreatedAtDesc(Integer userId);
}
