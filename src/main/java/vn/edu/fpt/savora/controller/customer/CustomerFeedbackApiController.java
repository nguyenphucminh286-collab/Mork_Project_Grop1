package vn.edu.fpt.savora.controller.customer;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.dto.response.GuestFeedbackResponse;
import vn.edu.fpt.savora.service.FeedbackService;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerFeedbackApiController {

    private final FeedbackService feedbackService;

    @GetMapping("/feedback")
    public ApiResponse<List<GuestFeedbackResponse>> myFeedback(HttpSession session) {
        AuthUserResponse authUser = session != null ? (AuthUserResponse) session.getAttribute("authUser") : null;
        if (authUser == null || authUser.getEmail() == null) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(feedbackService.getFeedbackForEmail(authUser.getEmail()));
    }
}
