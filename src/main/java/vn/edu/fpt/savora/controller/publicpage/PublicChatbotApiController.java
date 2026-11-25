package vn.edu.fpt.savora.controller.publicpage;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.savora.common.ApiResponse;
import vn.edu.fpt.savora.service.SavoraAiChatbotService;

import java.util.Map;

@RestController
@RequestMapping("/api/public/chatbot")
@RequiredArgsConstructor
public class PublicChatbotApiController {

    private final SavoraAiChatbotService savoraAiChatbotService;

    @PostMapping("/ask")
    public ApiResponse<Map<String, Object>> askChatbot(@RequestBody Map<String, String> payload) {
        String message = payload != null ? payload.get("message") : "";
        Map<String, Object> reply = savoraAiChatbotService.processCustomerMessage(message);
        return ApiResponse.ok(reply);
    }
}
