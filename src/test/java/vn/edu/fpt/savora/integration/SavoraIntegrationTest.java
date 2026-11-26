package vn.edu.fpt.savora.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.repository.AiChatConfigRepository;
import vn.edu.fpt.savora.repository.AiChatUsageLogRepository;
import vn.edu.fpt.savora.repository.UserRepository;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("render")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:savora_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "ai.gemini.api-key=",
        "gemini.api-key=",
        "ai.gemini.model=gemini-3.8-flash",
        "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration"
})
class SavoraIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiChatConfigRepository aiChatConfigRepository;

    @Autowired
    private AiChatUsageLogRepository aiChatUsageLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        aiChatUsageLogRepository.deleteAll();
        aiChatConfigRepository.deleteAll();
    }

    @Test
    void homePageRendersHeaderWithoutWrapperDivForStickyNavbar() throws Exception {
        MvcResult result = mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/assets/css/public/global.css?v=4")))
                .andExpect(content().string(containsString("/assets/js/public/chatbot.js?v=5")))
                .andExpect(content().string(containsString("savora-chat-clear")))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        int bodyStart = html.indexOf("<body>");
        int topbarStart = html.indexOf("<div class=\"topbar\"");
        int headerEnd = html.indexOf("</header>");
        int mainStart = html.indexOf("<main");
        assertTrue(bodyStart >= 0 && topbarStart > bodyStart && headerEnd > topbarStart && mainStart > headerEnd,
                "Expected <body> -> .topbar -> </header> -> <main> ordering");

        String betweenBodyAndTopbar = html.substring(bodyStart + "<body>".length(), topbarStart)
                .replaceAll("(?s)<!--.*?-->", "")
                .trim();
        String betweenHeaderAndMain = html.substring(headerEnd + "</header>".length(), mainStart)
                .replaceAll("(?s)<!--.*?-->", "")
                .trim();
        assertEquals("", betweenBodyAndTopbar, "Expected no wrapper element between <body> and .topbar");
        assertEquals("", betweenHeaderAndMain, "Expected no closing wrapper element between </header> and <main>");
    }

    @Test
    void publicChatbotAndAdminAiTokenManagementEndToEnd() throws Exception {
        mockMvc.perform(get("/api/public/chatbot/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiActive").value(false))
                .andExpect(jsonPath("$.data.provider").value("GEMINI"));

        mockMvc.perform(post("/api/public/chatbot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What vouchers do you have?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.answer", containsString("SAVORA10")));

        mockMvc.perform(post("/api/public/chatbot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"2 + 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.mode").value("GUARDRAIL"));

        // Admin endpoint requires authenticated admin session via AuthInterceptor
        mockMvc.perform(get("/api/admin/ai-settings"))
                .andExpect(status().isUnauthorized());

        User admin = userRepository.findByEmailIgnoreCase("admin@savora.vn").orElseThrow();
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("userId", admin.getUserId());
        adminSession.setAttribute("roleId", 1);
        adminSession.setAttribute("roleName", "ADMIN");
        adminSession.setAttribute("fullName", "Admin Savora");

        mockMvc.perform(post("/api/admin/ai-settings")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"AIzaSyDemoAdminToken8899\",\"modelName\":\"gemini-2.5-flash\",\"aiEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasToken").value(true))
                .andExpect(jsonPath("$.data.tokenSource").value("ADMIN_DB"))
                .andExpect(jsonPath("$.data.maskedToken").value("AIza••••••8899"));

        // Verify public status now reflects active Gemini token, without exposing raw token
        mockMvc.perform(get("/api/public/chatbot/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiActive").value(true))
                .andExpect(jsonPath("$.data.model").value("gemini-2.5-flash"));

        // Verify Admin Dashboard page renders AI tab cleanly
        mockMvc.perform(get("/admin/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"tab-ai\"")))
                .andExpect(content().string(containsString("/assets/js/admin/admin.js?v=17")));
    }
}
