package vn.edu.fpt.savora.chatbot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.fpt.savora.entity.AiChatConfig;
import vn.edu.fpt.savora.entity.AiChatUsageLog;
import vn.edu.fpt.savora.entity.Dish;
import vn.edu.fpt.savora.entity.Voucher;
import vn.edu.fpt.savora.enums.DishStatus;
import vn.edu.fpt.savora.enums.VoucherStatus;
import vn.edu.fpt.savora.repository.*;
import vn.edu.fpt.savora.service.GeminiApiClient;
import vn.edu.fpt.savora.service.SavoraAiChatbotService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavoraAiChatbotServiceTest {

    @Mock
    private DishRepository dishRepository;
    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private AiChatConfigRepository aiChatConfigRepository;
    @Mock
    private AiChatUsageLogRepository aiChatUsageLogRepository;
    @Mock
    private GeminiApiClient geminiApiClient;

    @InjectMocks
    private SavoraAiChatbotService chatbotService;

    @Test
    void processCustomerMessage_withGeminiToken_usesGeminiAndRecordsTokenUsage() {
        Dish steak = Dish.builder()
                .dishId(1)
                .dishName("Australian Wagyu Tenderloin")
                .price(BigDecimal.valueOf(420000))
                .discountPrice(BigDecimal.valueOf(389000))
                .isSignature(true)
                .status(DishStatus.AVAILABLE)
                .build();
        Voucher voucher = Voucher.builder()
                .voucherId(1)
                .voucherCode("SAVORA10")
                .discountPercent(10)
                .description("10% OFF")
                .status(VoucherStatus.ACTIVE)
                .build();

        AiChatConfig config = AiChatConfig.builder()
                .configId(1)
                .provider("GEMINI")
                .apiToken("AIzaSyTestSecretToken1234")
                .modelName("gemini-2.5-flash")
                .aiEnabled(true)
                .temperature(0.7)
                .maxOutputTokens(1024)
                .build();

        when(dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE)).thenReturn(List.of(steak));
        when(diningTableRepository.findAllByOrderByTableCodeAsc()).thenReturn(List.of());
        when(voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE)).thenReturn(List.of(voucher));
        when(aiChatConfigRepository.findFirstByOrderByConfigIdAsc()).thenReturn(Optional.of(config));

        when(geminiApiClient.generateContent(
                eq("AIzaSyTestSecretToken1234"),
                eq("gemini-2.5-flash"),
                argThat(prompt -> prompt.contains("Australian Wagyu Tenderloin") && prompt.contains("SAVORA10")),
                argThat(turns -> turns.size() == 2 && "user".equals(turns.get(1).role())),
                eq(0.7),
                eq(1024)
        )).thenReturn(new GeminiApiClient.GeminiResult(
                true,
                "Our signature Australian Wagyu Tenderloin is 389,000 VND!",
                250, 24, 274, 310L, null, 200
        ));

        Map<String, Object> reply = chatbotService.processCustomerMessage(
                "What is the price of Wagyu steak?",
                List.of(Map.of("role", "user", "text", "Hello"))
        );

        assertEquals(false, reply.get("blocked"));
        assertEquals("GEMINI", reply.get("mode"));
        assertEquals("gemini-2.5-flash", reply.get("model"));
        assertTrue(String.valueOf(reply.get("answer")).contains("389,000 VND"));

        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) reply.get("usage");
        assertEquals(274, usage.get("totalTokens"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> links = (List<Map<String, String>>) reply.get("links");
        assertFalse(links.isEmpty());

        ArgumentCaptor<AiChatUsageLog> logCaptor = ArgumentCaptor.forClass(AiChatUsageLog.class);
        verify(aiChatUsageLogRepository).save(logCaptor.capture());
        assertEquals("SUCCESS", logCaptor.getValue().getStatus());
        assertEquals(274, logCaptor.getValue().getTotalTokens());
    }

    @Test
    void processCustomerMessage_offTopicWithoutToken_isBlockedByGuardrail() {
        when(dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE)).thenReturn(List.of());
        when(diningTableRepository.findAllByOrderByTableCodeAsc()).thenReturn(List.of());
        when(voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE)).thenReturn(List.of());

        Map<String, Object> reply = chatbotService.processCustomerMessage("Viết giúp anh thuật toán quick sort bằng Java");

        assertEquals(true, reply.get("blocked"));
        assertEquals("GUARDRAIL", reply.get("mode"));
        assertTrue(String.valueOf(reply.get("answer")).contains("Savora"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> links = (List<Map<String, String>>) reply.get("links");
        assertEquals(4, links.size());
        verifyNoInteractions(geminiApiClient, aiChatConfigRepository);
    }

    @Test
    void saveAdminAiSettings_masksTokenAndPreservesExistingWhenBlankOrMasked() {
        AiChatConfig existing = AiChatConfig.builder()
                .configId(1)
                .provider("GEMINI")
                .apiToken("AIzaSyOriginalToken9999")
                .modelName("gemini-2.5-flash")
                .aiEnabled(true)
                .temperature(0.7)
                .maxOutputTokens(1024)
                .build();

        when(aiChatConfigRepository.findFirstByOrderByConfigIdAsc()).thenReturn(Optional.of(existing));
        when(aiChatConfigRepository.save(any(AiChatConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiChatUsageLogRepository.findTop50ByOrderByCreatedAtDescLogIdDesc()).thenReturn(List.of());

        Map<String, Object> updated = chatbotService.saveAdminAiSettings(
                Map.of("apiToken", "AIza••••••9999", "modelName", "gemini-2.5-pro", "aiEnabled", true),
                "Admin Savora"
        );

        assertEquals("AIzaSyOriginalToken9999", existing.getApiToken());
        assertEquals("gemini-2.5-pro", updated.get("modelName"));
        assertEquals("AIza••••••9999", updated.get("maskedToken"));
        assertEquals("ADMIN_DB", updated.get("tokenSource"));
        assertFalse(updated.values().toString().contains("AIzaSyOriginalToken9999"));
    }

    @Test
    void testGeminiConnection_returnsConnectedAndLogsUsage() {
        when(aiChatConfigRepository.findFirstByOrderByConfigIdAsc()).thenReturn(Optional.empty());
        when(geminiApiClient.generateContent(
                eq("AIzaSyNewTestKey5555"),
                eq("gemini-2.5-flash"),
                anyString(),
                anyList(),
                eq(0.2),
                eq(128)
        )).thenReturn(new GeminiApiClient.GeminiResult(
                true, "Savora Gemini AI is ready!", 18, 7, 25, 195L, null, 200
        ));

        Map<String, Object> res = chatbotService.testGeminiConnection("AIzaSyNewTestKey5555", "gemini-2.5-flash");

        assertEquals(true, res.get("connected"));
        assertEquals(25, res.get("totalTokens"));
        assertEquals("Savora Gemini AI is ready!", res.get("reply"));
        verify(aiChatUsageLogRepository).save(any(AiChatUsageLog.class));
    }

    @Test
    void processCustomerMessage_offTopicEnglishQuery_isBlockedBeforeCallingGemini() {
        when(dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE)).thenReturn(List.of());
        when(diningTableRepository.findAllByOrderByTableCodeAsc()).thenReturn(List.of());
        when(voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE)).thenReturn(List.of());

        Map<String, Object> reply = chatbotService.processCustomerMessage("Explain QuickSort algorithm");

        assertEquals("GUARDRAIL", reply.get("mode"));
        assertEquals(true, reply.get("blocked"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> links = (List<Map<String, String>>) reply.get("links");
        assertEquals(4, links.size());
        verifyNoInteractions(geminiApiClient, aiChatConfigRepository);
    }

    @Test
    void processCustomerMessage_geminiOutOfScopeMarker_stripsMarkerAndMarksBlocked() {
        AiChatConfig config = AiChatConfig.builder()
                .configId(1)
                .provider("GEMINI")
                .apiToken("AIzaSyTestSecretToken1234")
                .modelName("gemini-3.8-flash")
                .aiEnabled(true)
                .temperature(0.7)
                .maxOutputTokens(1024)
                .build();

        when(dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE)).thenReturn(List.of());
        when(diningTableRepository.findAllByOrderByTableCodeAsc()).thenReturn(List.of());
        when(voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE)).thenReturn(List.of());
        when(aiChatConfigRepository.findFirstByOrderByConfigIdAsc()).thenReturn(Optional.of(config));
        when(geminiApiClient.generateContent(
                eq("AIzaSyTestSecretToken1234"),
                eq("gemini-3.8-flash"),
                anyString(),
                anyList(),
                eq(0.7),
                eq(1024)
        )).thenReturn(new GeminiApiClient.GeminiResult(
                true,
                "[SAVORA_OUT_OF_SCOPE] Tôi chỉ hỗ trợ câu hỏi về nhà hàng Savora.",
                180, 15, 195, 220L, null, 200
        ));

        Map<String, Object> reply = chatbotService.processCustomerMessage("Chủ tịch nước hiện tại là ai?");

        assertEquals("GEMINI", reply.get("mode"));
        assertEquals(true, reply.get("blocked"));
        assertEquals("Tôi chỉ hỗ trợ câu hỏi về nhà hàng Savora.", reply.get("answer"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> links = (List<Map<String, String>>) reply.get("links");
        assertEquals(4, links.size());
    }

    @Test
    void saveAdminAiSettings_stripsAccidentalKeyPrefixWhenPasted() {
        java.util.concurrent.atomic.AtomicReference<AiChatConfig> store = new java.util.concurrent.atomic.AtomicReference<>();
        when(aiChatConfigRepository.findFirstByOrderByConfigIdAsc()).thenAnswer(inv -> Optional.ofNullable(store.get()));
        when(aiChatConfigRepository.save(any(AiChatConfig.class))).thenAnswer(inv -> {
            AiChatConfig cfg = inv.getArgument(0);
            store.set(cfg);
            return cfg;
        });
        when(aiChatUsageLogRepository.findTop50ByOrderByCreatedAtDescLogIdDesc()).thenReturn(List.of());

        Map<String, Object> saved = chatbotService.saveAdminAiSettings(
                Map.of("apiToken", "gemini.api-key=AQ.Ab8RN6SampleToken9999", "modelName", "gemini-3.8-flash"),
                "Admin Savora"
        );

        assertEquals("AQ.Ab8RN6SampleToken9999", store.get().getApiToken());
        assertEquals("AQ.A••••••9999", saved.get("maskedToken"));
        assertEquals("ADMIN_DB", saved.get("tokenSource"));
    }

    @Test
    void processCustomerMessage_whenPrimaryModelReturns503_retriesAndFallsBackToGemini35FlashLite() {
        AiChatConfig config = AiChatConfig.builder()
                .configId(1)
                .provider("GEMINI")
                .apiToken("AQ.Ab8RN6SampleToken9999")
                .modelName("gemini-3.8-flash")
                .aiEnabled(true)
                .temperature(0.7)
                .maxOutputTokens(2048)
                .build();

        when(dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE)).thenReturn(List.of());
        when(diningTableRepository.findAllByOrderByTableCodeAsc()).thenReturn(List.of());
        when(voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE)).thenReturn(List.of());
        when(aiChatConfigRepository.findFirstByOrderByConfigIdAsc()).thenReturn(Optional.of(config));

        when(geminiApiClient.generateContent(
                eq("AQ.Ab8RN6SampleToken9999"),
                eq("gemini-3.8-flash"),
                anyString(), anyList(), eq(0.7), eq(2048)
        )).thenReturn(new GeminiApiClient.GeminiResult(
                false, null, 0, 0, 0, 120L, "This model is currently experiencing high demand.", 503
        ));

        when(geminiApiClient.generateContent(
                eq("AQ.Ab8RN6SampleToken9999"),
                eq("gemini-3.5-flash-lite"),
                anyString(), anyList(), eq(0.7), eq(2048)
        )).thenReturn(new GeminiApiClient.GeminiResult(
                true, "Xin chào Long! Tôi là trợ lý AI của Savora.", 150, 18, 168, 250L, null, 200
        ));

        Map<String, Object> reply = chatbotService.processCustomerMessage("tôi là long bạn là ai");

        assertEquals("GEMINI", reply.get("mode"));
        assertEquals("gemini-3.5-flash-lite", reply.get("model"));
        assertTrue(String.valueOf(reply.get("answer")).contains("Savora"));
    }
}
