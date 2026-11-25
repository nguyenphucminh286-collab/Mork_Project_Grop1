package vn.edu.fpt.savora.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_activity_logs",
        indexes = {
                @Index(name = "idx_activity_created", columnList = "created_at"),
                @Index(name = "idx_activity_action", columnList = "action_type, created_at"),
                @Index(name = "idx_activity_user", columnList = "user_id, created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_email", length = 150)
    private String userEmail;

    @Column(name = "user_full_name", length = 150)
    private String userFullName;

    @Column(name = "user_role", length = 40)
    private String userRole;

    /**
     * LOGIN or PAGE_VISIT
     */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "page_path", nullable = false, length = 255)
    private String pagePath;

    @Column(name = "page_title", length = 200)
    private String pageTitle;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "location", length = 160)
    private String location;

    @Column(name = "device_info", length = 160)
    private String deviceInfo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
