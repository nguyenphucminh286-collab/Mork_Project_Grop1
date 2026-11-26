package vn.edu.fpt.savora.chatbot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.fpt.savora.controller.admin.AdminAiApiController;
import vn.edu.fpt.savora.controller.publicpage.PublicChatbotApiController;
import vn.edu.fpt.savora.exception.GlobalExceptionHandler;
import vn.edu.fpt.savora.service.SavoraAiChatbotService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatbotControllersTest {

    @Mock
    private SavoraAiChatbotService chatbotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new PublicChatbotApiController(chatbotService),
                        new AdminAiApiController(chatbotService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicStatusAndAskEndpoint() throws Exception {
        when(chatbotService.getPublicAiStatus()).thenReturn(Map.of(
                "aiActive", true,
                "provider", "GEMINI",
                "model", "gemini-2.5-flash"
        ));
        when(chatbotService.processCustomerMessage(eq("Hi Gemini"), anyList())).thenReturn(Map.of(
                "blocked", false,
                "mode", "GEMINI",
                "model", "gemini-2.5-flash",
                "answer", "Hello! How can I assist you?",
                "links", List.of(),
                "usage", Map.of("promptTokens", 20, "outputTokens", 10, "totalTokens", 30)
        ));

        mockMvc.perform(get("/api/public/chatbot/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aiActive").value(true))
                .andExpect(jsonPath("$.data.model").value("gemini-2.5-flash"));

        mockMvc.perform(post("/api/public/chatbot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi Gemini\",\"history\":[{\"role\":\"user\",\"text\":\"Prev\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mode").value("GEMINI"))
                .andExpect(jsonPath("$.data.usage.totalTokens").value(30));
    }

    @Test
    void adminAiSettingsAndTestEndpoints() throws Exception {
        when(chatbotService.getAdminAiSettings()).thenReturn(Map.of(
                "aiEnabled", true,
                "hasToken", true,
                "maskedToken", "AIza••••••9999",
                "modelName", "gemini-2.5-flash"
        ));
        when(chatbotService.saveAdminAiSettings(anyMap(), eq("Admin Savora"))).thenReturn(Map.of(
                "aiEnabled", true,
                "hasToken", true,
                "maskedToken", "AIza••••••1234",
                "modelName", "gemini-2.5-pro"
        ));
        when(chatbotService.testGeminiConnection("AIzaTest1234", "gemini-2.5-pro")).thenReturn(Map.of(
                "connected", true,
                "model", "gemini-2.5-pro",
                "reply", "Connected!",
                "totalTokens", 22
        ));

        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("fullName", "Admin Savora");

        mockMvc.perform(get("/api/admin/ai-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedToken").value("AIza••••••9999"));

        mockMvc.perform(post("/api/admin/ai-settings")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"AIzaTest1234\",\"modelName\":\"gemini-2.5-pro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").value("gemini-2.5-pro"));

        mockMvc.perform(post("/api/admin/ai-settings/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"AIzaTest1234\",\"modelName\":\"gemini-2.5-pro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.totalTokens").value(22));

        mockMvc.perform(delete("/api/admin/ai-settings/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(chatbotService).clearUsageLogs();
    }
}
