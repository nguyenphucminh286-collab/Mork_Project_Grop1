package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.fpt.savora.entity.AiChatConfig;

import java.util.Optional;

public interface AiChatConfigRepository extends JpaRepository<AiChatConfig, Integer> {
    Optional<AiChatConfig> findFirstByOrderByConfigIdAsc();
}
