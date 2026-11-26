package vn.edu.fpt.savora.controller.publicpage;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.service.SavoraAiChatbotService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/chatbot")
@RequiredArgsConstructor
public class PublicChatbotApiController {

    private final SavoraAiChatbotService savoraAiChatbotService;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getChatbotStatus() {
        return ApiResponse.ok(savoraAiChatbotService.getPublicAiStatus());
    }

    @PostMapping("/ask")
    public ApiResponse<Map<String, Object>> askChatbot(@RequestBody(required = false) Map<String, Object> payload) {
        String message = payload != null && payload.get("message") != null
                ? String.valueOf(payload.get("message"))
                : "";
        List<Map<String, String>> history = extractHistory(payload != null ? payload.get("history") : null);
        Map<String, Object> reply = savoraAiChatbotService.processCustomerMessage(message, history);
        return ApiResponse.ok(reply);
    }

    private List<Map<String, String>> extractHistory(Object rawHistory) {
        if (!(rawHistory instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, String>> history = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                Object roleObj = map.get("role");
                Object textObj = map.get("text");
                if (textObj != null) {
                    history.add(Map.of(
                            "role", roleObj != null ? String.valueOf(roleObj) : "user",
                            "text", String.valueOf(textObj)
                    ));
                }
            }
        }
        return history;
    }
}
