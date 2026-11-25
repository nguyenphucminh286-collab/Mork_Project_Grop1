package vn.edu.fpt.savora.controller.publicpage;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.service.SavoraEmailService;

import java.util.Map;

@RestController
@RequestMapping("/api/public/contact")
@RequiredArgsConstructor
public class PublicFeedbackApiController {

    private final SavoraEmailService savoraEmailService;

    @PostMapping("/feedback")
    public ApiResponse<Map<String, String>> submitFeedback(@RequestBody Map<String, String> payload) {
        String name = payload != null ? payload.get("name") : null;
        String email = payload != null ? payload.get("email") : null;
        String message = payload != null ? payload.get("message") : null;

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Please enter your full name.");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Please enter your feedback message.");
        }

        savoraEmailService.sendFeedbackConfirmationEmail(name.trim(), email.trim(), message.trim());

        return ApiResponse.ok(
                "We value your feedback and will get back to you soon!",
                Map.of(
                        "name", name.trim(),
                        "email", email.trim(),
                        "replyQuote", "We value your feedback and will get back to you soon!"
                )
        );
    }
}
