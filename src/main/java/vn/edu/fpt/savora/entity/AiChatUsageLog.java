package vn.edu.fpt.savora.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_chat_usage_logs",
        indexes = {
                @Index(name = "idx_ai_usage_created", columnList = "created_at"),
                @Index(name = "idx_ai_usage_status", columnList = "status, created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "prompt_tokens", nullable = false)
    @Builder.Default
    private Integer promptTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    @Builder.Default
    private Integer outputTokens = 0;

    @Column(name = "total_tokens", nullable = false)
    @Builder.Default
    private Integer totalTokens = 0;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "user_message_preview", length = 255)
    private String userMessagePreview;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.promptTokens == null) this.promptTokens = 0;
        if (this.outputTokens == null) this.outputTokens = 0;
        if (this.totalTokens == null) this.totalTokens = 0;
    }
}
