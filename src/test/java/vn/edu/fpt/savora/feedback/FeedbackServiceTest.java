package vn.edu.fpt.savora.feedback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edu.fpt.savora.dto.response.GuestFeedbackResponse;
import vn.edu.fpt.savora.entity.GuestFeedback;
import vn.edu.fpt.savora.enums.FeedbackStatus;
import vn.edu.fpt.savora.repository.GuestFeedbackRepository;
import vn.edu.fpt.savora.service.FeedbackService;
import vn.edu.fpt.savora.service.SavoraEmailService;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private GuestFeedbackRepository feedbackRepository;

    @Mock
    private SavoraEmailService emailService;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    void submitFeedback_savesWithCodeAndSendsConfirmationEmail() {
        when(feedbackRepository.existsByFeedbackCodeIgnoreCase(anyString())).thenReturn(false);
        when(feedbackRepository.save(any(GuestFeedback.class))).thenAnswer(inv -> {
            GuestFeedback f = inv.getArgument(0);
            setId(f, 10L);
            return f;
        });

        FeedbackService.SubmissionResult result = feedbackService.submitFeedback(
                "Minh Nguyen", "minh@gmail.com", "Great steak and atmosphere!"
        );

        assertTrue(result.emailSent());
        assertNotNull(result.feedback().getFeedbackCode());
        assertTrue(result.feedback().getFeedbackCode().startsWith("FB-"));
        assertEquals("PENDING", result.feedback().getStatus());
        verify(emailService).sendFeedbackConfirmationEmail(
                eq("Minh Nguyen"),
                eq("minh@gmail.com"),
                eq("Great steak and atmosphere!"),
                eq(result.feedback().getFeedbackCode())
        );
    }

    @Test
    void replyToFeedback_sendsEmailToGuestAndPersistsReply() {
        GuestFeedback existing = new GuestFeedback("FB-100200", "Lan Tran", "lantran@gmail.com", "Need faster dessert service.");
        setId(existing, 2L);
        when(feedbackRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.saveAndFlush(any(GuestFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

        FeedbackService.ReplyResult result = feedbackService.replyToFeedback(
                2L, "Thank you Lan, we have shared this with our pastry team.", "Admin Savora"
        );

        assertTrue(result.emailSent());
        assertEquals("REPLIED", result.feedback().getStatus());
        assertEquals("Thank you Lan, we have shared this with our pastry team.", result.feedback().getAdminReply());
        assertEquals("Admin Savora", result.feedback().getRepliedBy());
        assertNotNull(result.feedback().getRepliedAt());

        verify(emailService).sendFeedbackReplyEmail(
                "Lan Tran",
                "lantran@gmail.com",
                "Need faster dessert service.",
                "Thank you Lan, we have shared this with our pastry team.",
                "Admin Savora",
                "FB-100200"
        );
    }

    @Test
    void replyToFeedback_whenSmtpFails_stillSavesReplyAndFlagsEmailFailure() {
        GuestFeedback existing = new GuestFeedback(null, "mr vnd", "mrvnd@gmail.com", "hahahaaaaa");
        setId(existing, 1L);
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.saveAndFlush(any(GuestFeedback.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalArgumentException("SMTP timeout"))
                .when(emailService)
                .sendFeedbackReplyEmail(any(), any(), any(), any(), any(), any());

        FeedbackService.ReplyResult result = feedbackService.replyToFeedback(1L, "Thank you for visiting!", "Admin");

        assertFalse(result.emailSent());
        assertEquals("FB-1", result.feedback().getFeedbackCode());
        assertEquals("REPLIED", result.feedback().getStatus());
        assertEquals("Thank you for visiting!", result.feedback().getAdminReply());
        assertTrue(result.notice().contains("Gmail delivery failed"));
    }

    @Test
    void lookupFeedback_matchesLegacyCodeFallbackAndEmail() {
        GuestFeedback legacy = new GuestFeedback(null, "mr vnd", "mrvnd@gmail.com", "hahahaaaaa");
        setId(legacy, 1L);
        legacy.recordReply("We appreciate your message!", "Admin Savora", true);

        when(feedbackRepository.findByEmailIgnoreCaseOrderBySubmittedAtDescIdDesc("mrvnd@gmail.com"))
                .thenReturn(List.of(legacy));

        GuestFeedbackResponse response = feedbackService.lookupFeedback("fb-1", "mrvnd@gmail.com");
        assertEquals("FB-1", response.getFeedbackCode());
        assertEquals("REPLIED", response.getStatus());
        assertEquals("We appreciate your message!", response.getAdminReply());
    }

    @Test
    void getAdminFeedbackInbox_returnsCountsAndPagedItems() {
        GuestFeedback f1 = new GuestFeedback("FB-100001", "Guest 1", "g1@gmail.com", "Hello");
        setId(f1, 1L);
        when(feedbackRepository.search("", FeedbackStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(f1), PageRequest.of(0, 10), 1));
        when(feedbackRepository.countPending()).thenReturn(1L);
        when(feedbackRepository.countByStatus(FeedbackStatus.REPLIED)).thenReturn(3L);

        Map<String, Object> inbox = feedbackService.getAdminFeedbackInbox("", "PENDING", 0);
        assertEquals(4L, inbox.get("totalAll"));
        assertEquals(1L, inbox.get("pendingCount"));
        assertEquals(3L, inbox.get("repliedCount"));
        assertEquals(1L, inbox.get("total"));
    }

    private static void setId(GuestFeedback target, Long id) {
        try {
            Field f = GuestFeedback.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
