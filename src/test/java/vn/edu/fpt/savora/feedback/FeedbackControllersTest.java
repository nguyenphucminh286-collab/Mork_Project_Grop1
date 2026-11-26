package vn.edu.fpt.savora.feedback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.fpt.savora.controller.admin.AdminApiController;
import vn.edu.fpt.savora.controller.customer.CustomerFeedbackApiController;
import vn.edu.fpt.savora.controller.publicpage.PublicFeedbackApiController;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.dto.response.GuestFeedbackResponse;
import vn.edu.fpt.savora.exception.GlobalExceptionHandler;
import vn.edu.fpt.savora.service.AdminPortalService;
import vn.edu.fpt.savora.service.CloudinaryUploadService;
import vn.edu.fpt.savora.service.FeedbackService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FeedbackControllersTest {

    @Mock
    private FeedbackService feedbackService;
    @Mock
    private AdminPortalService adminPortalService;
    @Mock
    private CloudinaryUploadService cloudinaryUploadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new PublicFeedbackApiController(feedbackService),
                        new AdminApiController(adminPortalService, cloudinaryUploadService, feedbackService),
                        new CustomerFeedbackApiController(feedbackService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicSubmitAndLookupFeedback() throws Exception {
        GuestFeedbackResponse dto = GuestFeedbackResponse.builder()
                .id(5L)
                .feedbackCode("FB-555111")
                .name("Alice")
                .email("alice@gmail.com")
                .message("Delicious lobster!")
                .status("PENDING")
                .submittedAt(LocalDateTime.now())
                .emailSent(false)
                .build();
        when(feedbackService.submitFeedback("Alice", "alice@gmail.com", "Delicious lobster!"))
                .thenReturn(new FeedbackService.SubmissionResult(dto, "Thanks! Reference code: FB-555111", true));
        when(feedbackService.lookupFeedback("FB-555111", "alice@gmail.com")).thenReturn(dto);

        mockMvc.perform(post("/api/public/contact/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"email\":\"alice@gmail.com\",\"message\":\"Delicious lobster!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.feedbackCode").value("FB-555111"));

        mockMvc.perform(get("/api/public/contact/feedback/lookup")
                        .param("code", "FB-555111")
                        .param("email", "alice@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alice"));
    }

    @Test
    void adminReplyAndCustomerFetchReplies() throws Exception {
        GuestFeedbackResponse replied = GuestFeedbackResponse.builder()
                .id(5L)
                .feedbackCode("FB-555111")
                .name("Alice")
                .email("alice@gmail.com")
                .message("Delicious lobster!")
                .status("REPLIED")
                .adminReply("Thank you Alice!")
                .repliedBy("Admin Savora")
                .repliedAt(LocalDateTime.now())
                .emailSent(true)
                .build();

        when(feedbackService.replyToFeedback(5L, "Thank you Alice!", "Admin Savora"))
                .thenReturn(new FeedbackService.ReplyResult(replied, "Reply saved and sent to alice@gmail.com!", true));
        when(feedbackService.getFeedbackForEmail("alice@gmail.com")).thenReturn(List.of(replied));

        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("fullName", "Admin Savora");

        mockMvc.perform(post("/api/admin/feedback/5/reply")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replyMessage\":\"Thank you Alice!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailSent").value(true))
                .andExpect(jsonPath("$.data.feedback.status").value("REPLIED"));

        MockHttpSession customerSession = new MockHttpSession();
        customerSession.setAttribute("authUser", AuthUserResponse.builder()
                .userId(2)
                .email("alice@gmail.com")
                .build());

        mockMvc.perform(get("/api/customer/feedback").session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].adminReply").value("Thank you Alice!"));

        when(feedbackService.getAdminFeedbackInbox("", "ALL", 0))
                .thenReturn(Map.of("items", List.of(replied), "total", 1, "page", 0, "pages", 1));
        mockMvc.perform(get("/api/admin/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].feedbackCode").value("FB-555111"));

        mockMvc.perform(delete("/api/admin/feedback/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(feedbackService).deleteFeedback(eq(5L));
    }
}
