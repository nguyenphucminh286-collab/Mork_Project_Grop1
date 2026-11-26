package vn.edu.fpt.savora.controller.publicpage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.dto.response.GuestFeedbackResponse;
import vn.edu.fpt.savora.service.FeedbackService;

import java.util.Map;

@RestController
@RequestMapping("/api/public/contact")
@RequiredArgsConstructor
public class PublicFeedbackApiController {

    private final FeedbackService feedbackService;

    public record FeedbackForm(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 10000) String message
    ) {
        public FeedbackForm {
            name = name == null ? null : name.strip();
            email = email == null ? null : email.strip();
            message = message == null ? null : message.strip();
        }
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> submitFeedback(@Valid @RequestBody FeedbackForm form) {
        FeedbackService.SubmissionResult result = feedbackService.submitFeedback(form.name(), form.email(), form.message());
        return ApiResponse.ok(result.notice(), Map.of(
                "name", form.name(),
                "email", form.email(),
                "feedbackCode", result.feedback().getFeedbackCode(),
                "emailSent", result.emailSent(),
                "feedback", result.feedback(),
                "replyQuote", "We value your feedback and will get back to you soon!"
        ));
    }

    @GetMapping("/feedback/lookup")
    public ApiResponse<GuestFeedbackResponse> lookupFeedback(
            @RequestParam String code,
            @RequestParam String email
    ) {
        return ApiResponse.ok(feedbackService.lookupFeedback(code, email));
    }
}
