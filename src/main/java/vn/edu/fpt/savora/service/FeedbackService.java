package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.dto.response.GuestFeedbackResponse;
import vn.edu.fpt.savora.entity.GuestFeedback;
import vn.edu.fpt.savora.enums.FeedbackStatus;
import vn.edu.fpt.savora.repository.GuestFeedbackRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private static final int ADMIN_PAGE_SIZE = 10;

    private final GuestFeedbackRepository feedbackRepository;
    private final SavoraEmailService savoraEmailService;

    public record SubmissionResult(GuestFeedbackResponse feedback, String notice, boolean emailSent) {}

    public record ReplyResult(GuestFeedbackResponse feedback, String notice, boolean emailSent) {}

    @Transactional
    public SubmissionResult submitFeedback(String name, String email, String message) {
        String code = generateUniqueCode();
        GuestFeedback saved = feedbackRepository.save(new GuestFeedback(code, name, email, message));

        String notice = "We value your feedback and will get back to you soon! Reference code: " + saved.resolvedCode();
        boolean emailSent = true;
        try {
            savoraEmailService.sendFeedbackConfirmationEmail(name, email, message, saved.resolvedCode());
        } catch (IllegalArgumentException ex) {
            emailSent = false;
            log.warn("Could not send the confirmation email for feedback {}: {}", saved.getId(), ex.getMessage());
            notice = "Your feedback (" + saved.resolvedCode() + ") was received. Confirmation email could not be delivered right now.";
        }
        return new SubmissionResult(toResponse(saved), notice, emailSent);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminFeedbackInbox(String search, String statusParam, int page) {
        FeedbackStatus statusFilter = parseStatusFilter(statusParam);
        String term = search != null ? search.strip() : "";
        Page<GuestFeedback> result = feedbackRepository.search(
                term,
                statusFilter,
                PageRequest.of(Math.max(0, page), ADMIN_PAGE_SIZE)
        );

        long pendingCount = feedbackRepository.countPending();
        long repliedCount = feedbackRepository.countByStatus(FeedbackStatus.REPLIED);
        long totalAll = pendingCount + repliedCount;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", result.getContent().stream().map(this::toResponse).toList());
        payload.put("total", result.getTotalElements());
        payload.put("page", result.getNumber());
        payload.put("pages", result.getTotalPages());
        payload.put("totalAll", totalAll);
        payload.put("pendingCount", pendingCount);
        payload.put("repliedCount", repliedCount);
        return payload;
    }

    @Transactional
    public ReplyResult replyToFeedback(Long id, String replyMessage, String responderName) {
        if (replyMessage == null || replyMessage.isBlank()) {
            throw new IllegalArgumentException("Please enter a reply message.");
        }
        GuestFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Feedback #" + id + " was not found."));

        feedback.ensureFeedbackCode("FB-" + feedback.getId());
        String cleanReply = replyMessage.strip();
        String actor = (responderName != null && !responderName.isBlank()) ? responderName.strip() : "Savora Admin";

        boolean emailDelivered = true;
        String emailError = null;
        try {
            savoraEmailService.sendFeedbackReplyEmail(
                    feedback.getName(),
                    feedback.getEmail(),
                    feedback.getMessage(),
                    cleanReply,
                    actor,
                    feedback.resolvedCode()
            );
        } catch (IllegalArgumentException ex) {
            emailDelivered = false;
            emailError = ex.getMessage();
            log.warn("Could not send feedback reply email to {} for feedback {}: {}",
                    feedback.getEmail(), feedback.getId(), emailError);
        }

        feedback.recordReply(cleanReply, actor, emailDelivered);
        GuestFeedback saved = feedbackRepository.saveAndFlush(feedback);

        String notice = emailDelivered
                ? "Reply saved and sent to " + saved.getEmail() + "!"
                : "Reply saved in system, but Gmail delivery failed: " + emailError;

        return new ReplyResult(toResponse(saved), notice, emailDelivered);
    }

    @Transactional
    public void deleteFeedback(Long id) {
        GuestFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Feedback #" + id + " was not found."));
        feedbackRepository.delete(feedback);
    }

    @Transactional(readOnly = true)
    public List<GuestFeedbackResponse> getFeedbackForEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return feedbackRepository.findByEmailIgnoreCaseOrderBySubmittedAtDescIdDesc(email.strip())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuestFeedbackResponse lookupFeedback(String feedbackCode, String email) {
        if (feedbackCode == null || feedbackCode.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Please enter both your feedback code and email address.");
        }
        String normalizedCode = feedbackCode.strip().toUpperCase(Locale.ROOT);
        String normalizedEmail = email.strip();

        return feedbackRepository.findByEmailIgnoreCaseOrderBySubmittedAtDescIdDesc(normalizedEmail)
                .stream()
                .filter(f -> normalizedCode.equalsIgnoreCase(f.resolvedCode()))
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No feedback record matched code \"" + normalizedCode + "\" and email \"" + normalizedEmail + "\"."
                ));
    }

    public GuestFeedbackResponse toResponse(GuestFeedback f) {
        return GuestFeedbackResponse.builder()
                .id(f.getId())
                .feedbackCode(f.resolvedCode())
                .name(f.getName())
                .email(f.getEmail())
                .message(f.getMessage())
                .status(f.resolvedStatus().name())
                .adminReply(f.getAdminReply())
                .repliedBy(f.getRepliedBy())
                .submittedAt(f.getSubmittedAt())
                .repliedAt(f.getRepliedAt())
                .emailSent(f.isEmailSentFlag())
                .build();
    }

    private FeedbackStatus parseStatusFilter(String statusParam) {
        if (statusParam == null || statusParam.isBlank() || "ALL".equalsIgnoreCase(statusParam.trim())) {
            return null;
        }
        try {
            return FeedbackStatus.valueOf(statusParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 8; i++) {
            String candidate = "FB-" + ThreadLocalRandom.current().nextInt(100000, 999999);
            if (!feedbackRepository.existsByFeedbackCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return "FB-" + System.currentTimeMillis();
    }
}
