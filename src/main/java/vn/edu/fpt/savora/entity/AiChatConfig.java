package vn.edu.fpt.savora.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Integer configId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String provider = "GEMINI";

    @Column(name = "api_token", length = 512)
    private String apiToken;

    @Column(name = "model_name", nullable = false, length = 100)
    @Builder.Default
    private String modelName = "gemini-3.8-flash";

    @Column(name = "ai_enabled", nullable = false)
    @Builder.Default
    private Boolean aiEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Double temperature = 0.7;

    @Column(name = "max_output_tokens", nullable = false)
    @Builder.Default
    private Integer maxOutputTokens = 2048;

    @Column(name = "custom_system_prompt", columnDefinition = "TEXT")
    private String customSystemPrompt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
        if (this.provider == null || this.provider.isBlank()) {
            this.provider = "GEMINI";
        }
        if (this.modelName == null || this.modelName.isBlank()) {
            this.modelName = "gemini-3.8-flash";
        }
        if (this.aiEnabled == null) {
            this.aiEnabled = true;
        }
        if (this.temperature == null) {
            this.temperature = 0.7;
        }
        if (this.maxOutputTokens == null) {
            this.maxOutputTokens = 2048;
        }
    }
}
