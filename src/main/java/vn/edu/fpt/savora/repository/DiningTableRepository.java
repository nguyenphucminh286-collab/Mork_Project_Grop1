package vn.edu.fpt.savora.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.savora.entity.DiningTable;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiningTableRepository extends JpaRepository<DiningTable, Integer> {

    List<DiningTable> findAllByOrderByTableCodeAsc();

    Optional<DiningTable> findByTableCodeIgnoreCase(String tableCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DiningTable t WHERE t.tableId = :tableId")
    Optional<DiningTable> findByIdForUpdate(@Param("tableId") Integer tableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DiningTable t WHERE UPPER(t.tableCode) = UPPER(:tableCode)")
    Optional<DiningTable> findByTableCodeForUpdate(@Param("tableCode") String tableCode);
}
