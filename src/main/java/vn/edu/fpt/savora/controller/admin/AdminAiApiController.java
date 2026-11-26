package vn.edu.fpt.savora.controller.admin;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.service.SavoraAiChatbotService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-settings")
@RequiredArgsConstructor
public class AdminAiApiController {

    private final SavoraAiChatbotService savoraAiChatbotService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getAiSettings() {
        return ApiResponse.ok(savoraAiChatbotService.getAdminAiSettings());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> saveAiSettings(
            @RequestBody Map<String, Object> payload,
            HttpSession session
    ) {
        String actor = session != null && session.getAttribute("fullName") != null
                ? String.valueOf(session.getAttribute("fullName"))
                : "Savora Admin";
        Map<String, Object> updated = savoraAiChatbotService.saveAdminAiSettings(payload, actor);
        return ApiResponse.ok("Saved Gemini AI settings & token!", updated);
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testGeminiToken(
            @RequestBody(required = false) Map<String, String> payload
    ) {
        String token = payload != null ? payload.get("apiToken") : null;
        String model = payload != null ? payload.get("modelName") : null;
        Map<String, Object> result = savoraAiChatbotService.testGeminiConnection(token, model);
        String msg = Boolean.TRUE.equals(result.get("connected"))
                ? "Gemini API token connected successfully!"
                : "Gemini API connection failed: " + result.get("errorMessage");
        return ApiResponse.ok(msg, result);
    }

    @DeleteMapping("/logs")
    public ApiResponse<Void> clearUsageLogs() {
        savoraAiChatbotService.clearUsageLogs();
        return ApiResponse.ok("Cleared AI token usage logs.", null);
    }
}
