package vn.edu.fpt.savora.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.fpt.savora.enums.FeedbackStatus;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "guest_feedback",
        indexes = {
                @Index(name = "idx_feedback_submitted", columnList = "submitted_at, id"),
                @Index(name = "idx_feedback_status", columnList = "status, submitted_at")
        }
)
public class GuestFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_code", length = 32, unique = true)
    private String feedbackCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FeedbackStatus status;

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    @Column(name = "replied_by", length = 150)
    private String repliedBy;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "email_sent")
    private Boolean emailSent;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    public GuestFeedback(String name, String email, String message) {
        this(null, name, email, message);
    }

    public GuestFeedback(String feedbackCode, String name, String email, String message) {
        this.feedbackCode = feedbackCode;
        this.name = name;
        this.email = email;
        this.message = message;
        this.status = FeedbackStatus.PENDING;
        this.emailSent = false;
        this.submittedAt = LocalDateTime.now();
    }

    public String resolvedCode() {
        if (feedbackCode != null && !feedbackCode.isBlank()) {
            return feedbackCode;
        }
        return id != null ? "FB-" + id : "FB-NEW";
    }

    public FeedbackStatus resolvedStatus() {
        return status != null ? status : FeedbackStatus.PENDING;
    }

    public boolean isEmailSentFlag() {
        return Boolean.TRUE.equals(emailSent);
    }

    public void ensureFeedbackCode(String code) {
        if ((this.feedbackCode == null || this.feedbackCode.isBlank()) && code != null && !code.isBlank()) {
            this.feedbackCode = code;
        }
    }

    public void recordReply(String replyContent, String responderName, boolean deliveredByEmail) {
        this.adminReply = replyContent;
        this.repliedBy = responderName;
        this.repliedAt = LocalDateTime.now();
        this.status = FeedbackStatus.REPLIED;
        this.emailSent = deliveredByEmail;
    }
}
