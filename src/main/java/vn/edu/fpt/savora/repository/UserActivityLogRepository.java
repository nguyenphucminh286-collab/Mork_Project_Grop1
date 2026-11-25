package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.savora.entity.UserActivityLog;

import java.util.List;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Integer> {

    List<UserActivityLog> findTop200ByOrderByCreatedAtDesc();

    List<UserActivityLog> findTop100ByActionTypeOrderByCreatedAtDesc(String actionType);
}
