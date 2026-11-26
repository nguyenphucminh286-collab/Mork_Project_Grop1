package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestFeedbackResponse {
    private Long id;
    private String feedbackCode;
    private String name;
    private String email;
    private String message;
    private String status;
    private String adminReply;
    private String repliedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime repliedAt;
    private boolean emailSent;
}
