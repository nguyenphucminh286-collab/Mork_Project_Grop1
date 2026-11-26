package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.fpt.savora.entity.AiChatUsageLog;

import java.time.LocalDateTime;
import java.util.List;

public interface AiChatUsageLogRepository extends JpaRepository<AiChatUsageLog, Integer> {

    List<AiChatUsageLog> findTop50ByOrderByCreatedAtDescLogIdDesc();

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(l.promptTokens), 0) FROM AiChatUsageLog l")
    long sumPromptTokens();

    @Query("SELECT COALESCE(SUM(l.outputTokens), 0) FROM AiChatUsageLog l")
    long sumOutputTokens();

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiChatUsageLog l")
    long sumTotalTokens();

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiChatUsageLog l WHERE l.createdAt >= :since")
    long sumTotalTokensSince(@Param("since") LocalDateTime since);
}
