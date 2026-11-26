package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.entity.AiChatConfig;
import vn.edu.fpt.savora.entity.AiChatUsageLog;
import vn.edu.fpt.savora.entity.DiningTable;
import vn.edu.fpt.savora.entity.Dish;
import vn.edu.fpt.savora.entity.Voucher;
import vn.edu.fpt.savora.enums.DishStatus;
import vn.edu.fpt.savora.enums.TableStatus;
import vn.edu.fpt.savora.enums.VoucherStatus;
import vn.edu.fpt.savora.repository.AiChatConfigRepository;
import vn.edu.fpt.savora.repository.AiChatUsageLogRepository;
import vn.edu.fpt.savora.repository.DiningTableRepository;
import vn.edu.fpt.savora.repository.DishRepository;
import vn.edu.fpt.savora.repository.VoucherRepository;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Domain-Locked Savora Website AI Concierge powered by Google Gemini API.
 * Answers strictly questions related to the Savora Fine Dining & Lounge website
 * (menu, tables, reservations, vouchers, QR ordering, hours, contact/feedback, accounts)
 * and blocks unrelated topics before/after Gemini calls.
 */
@Service
@RequiredArgsConstructor
public class SavoraAiChatbotService {

    private static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final int MAX_HISTORY_TURNS = 10;
    private static final String OUT_OF_SCOPE_MARKER = "[SAVORA_OUT_OF_SCOPE]";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern MATH_EXPR_PATTERN = Pattern.compile(".*\\b\\d+\\s*[+\\-*/^]\\s*\\d+\\b.*");
    private static final List<String> OFF_TOPIC_KEYWORDS = List.of(
            "python", "javascript", "java code", "c++", "html code", "write code", "viết code", "lập trình",
            "programming", "leetcode", "algorithm", "thuật toán", "quicksort", "react", "angular", "docker", "kubernetes",
            "bitcoin", "crypto", "ethereum", "stock market", "chứng khoán",
            "weather", "thời tiết", "nhiệt độ", "forecast", "president", "tổng thống", "politics", "chính trị", "election",
            "football", "bóng đá", "soccer", "world cup", "champions league", "messi", "ronaldo", "nba", "basketball",
            "capital of", "thủ đô", "who invented", "world war", "solar system", "physics", "vật lý", "quantum",
            "chemistry", "hóa học", "solve equation", "giải phương trình", "integral", "tích phân",
            "homework", "bài tập", "essay", "bài văn", "poem", "làm thơ", "write a story", "kể chuyện", "joke"
    );

    private final DishRepository dishRepository;
    private final DiningTableRepository diningTableRepository;
    private final VoucherRepository voucherRepository;
    private final AiChatConfigRepository aiChatConfigRepository;
    private final AiChatUsageLogRepository aiChatUsageLogRepository;
    private final GeminiApiClient geminiApiClient;

    @Value("${ai.gemini.api-key:}")
    private String envApiKey = "";

    @Value("${ai.gemini.model:" + DEFAULT_MODEL + "}")
    private String defaultModel = DEFAULT_MODEL;

    @Value("${ai.gemini.enabled:true}")
    private boolean defaultEnabled = true;

    public record ResolvedSettings(
            boolean aiEnabled,
            String apiToken,
            String tokenSource,
            String modelName,
            double temperature,
            int maxOutputTokens,
            String customSystemPrompt,
            boolean hasDbToken,
            boolean hasEnvToken
    ) {
        public boolean hasToken() {
            return apiToken != null && !apiToken.isBlank();
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicAiStatus() {
        ResolvedSettings settings = resolveSettings();
        boolean aiActive = settings.aiEnabled() && settings.hasToken();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("aiActive", aiActive);
        status.put("provider", "GEMINI");
        status.put("model", aiActive ? settings.modelName() : "LOCAL_ASSISTANT");
        return status;
    }

    @Transactional
    public Map<String, Object> processCustomerMessage(String rawMessage) {
        return processCustomerMessage(rawMessage, List.of());
    }

    @Transactional
    public Map<String, Object> processCustomerMessage(String rawMessage, List<Map<String, String>> history) {
        String msg = rawMessage != null ? rawMessage.trim() : "";
        if (msg.isEmpty()) {
            return buildReply(
                    false,
                    "Hello! I am the **Savora Website AI Assistant**. Please ask me anything about **Savora Restaurant** (signature menu dishes, live table availability, reservations, vouchers, QR ordering, or contact/feedback).",
                    List.of(
                            link("🪑 Book a Table", "/home#tables-section"),
                            link("🍽️ View Menu", "/home#menu"),
                            link("✉️ Contact & Feedback", "/contact")
                    ),
                    "LOCAL",
                    "Savora Local",
                    null
            );
        }

        List<Dish> dishes = dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE);
        List<DiningTable> tables = diningTableRepository.findAllByOrderByTableCodeAsc();
        List<Voucher> vouchers = voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE);

        if (isExplicitlyOffTopic(msg, dishes)) {
            recordUsageLog("GUARDRAIL", "domain-filter", "BLOCKED", 0, 0, 0, 0L, msg, "Off-topic query blocked");
            return buildBlockedReply(msg, "GUARDRAIL", "Savora Domain Guardrail", null);
        }

        ResolvedSettings settings = resolveSettings();
        if (settings.aiEnabled() && settings.hasToken()) {
            String systemPrompt = buildGeminiSystemPrompt(dishes, tables, vouchers, settings.customSystemPrompt());
            List<GeminiApiClient.ChatTurn> turns = buildConversationTurns(history, msg);

            String usedModel = settings.modelName();
            GeminiApiClient.GeminiResult result = geminiApiClient.generateContent(
                    settings.apiToken(),
                    usedModel,
                    systemPrompt,
                    turns,
                    settings.temperature(),
                    settings.maxOutputTokens()
            );

            if (!result.success() && result.httpStatus() == 503) {
                try {
                    Thread.sleep(600L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                result = geminiApiClient.generateContent(
                        settings.apiToken(),
                        usedModel,
                        systemPrompt,
                        turns,
                        settings.temperature(),
                        settings.maxOutputTokens()
                );
                if (!result.success() && (result.httpStatus() == 503 || result.httpStatus() == 429)
                        && !"gemini-3.5-flash-lite".equalsIgnoreCase(usedModel)) {
                    usedModel = "gemini-3.5-flash-lite";
                    result = geminiApiClient.generateContent(
                            settings.apiToken(),
                            usedModel,
                            systemPrompt,
                            turns,
                            settings.temperature(),
                            settings.maxOutputTokens()
                    );
                }
            }

            if (result.success()) {
                String rawText = result.text() != null ? result.text().trim() : "";
                boolean outOfScope = rawText.contains(OUT_OF_SCOPE_MARKER);
                String cleanText = rawText.replace(OUT_OF_SCOPE_MARKER, "").trim();

                recordUsageLog(
                        "GEMINI",
                        usedModel,
                        outOfScope ? "BLOCKED" : "SUCCESS",
                        result.promptTokens(),
                        result.outputTokens(),
                        result.totalTokens(),
                        result.latencyMs(),
                        msg,
                        outOfScope ? "Gemini refused off-topic query" : null
                );

                Map<String, Object> usage = Map.of(
                        "promptTokens", result.promptTokens(),
                        "outputTokens", result.outputTokens(),
                        "totalTokens", result.totalTokens(),
                        "latencyMs", result.latencyMs()
                );

                if (outOfScope) {
                    if (cleanText.isEmpty()) {
                        return buildBlockedReply(msg, "GEMINI", usedModel, usage);
                    }
                    return buildReply(
                            true,
                            cleanText,
                            defaultSavoraLinks(),
                            "GEMINI",
                            usedModel,
                            usage
                    );
                }

                List<Map<String, String>> relevantLinks = detectRelevantLinks(msg, cleanText, dishes);
                return buildReply(false, cleanText, relevantLinks, "GEMINI", usedModel, usage);
            }

            recordUsageLog(
                    "GEMINI",
                    usedModel,
                    "ERROR",
                    result.promptTokens(),
                    result.outputTokens(),
                    result.totalTokens(),
                    result.latencyMs(),
                    msg,
                    result.errorMessage()
            );

            Map<String, Object> localMatch = tryLocalRestaurantReply(msg, dishes, tables, vouchers);
            if (localMatch != null) {
                return localMatch;
            }

            return buildBlockedReply(msg, "FALLBACK", "Local Fallback", null);
        }

        // Token not configured or AI disabled -> try live DB local restaurant assistant first
        Map<String, Object> localMatch = tryLocalRestaurantReply(msg, dishes, tables, vouchers);
        if (localMatch != null) {
            recordUsageLog("LOCAL", "local-restaurant-rules", "FALLBACK", 0, 0, 0, 0L, msg, "Gemini token not active");
            return localMatch;
        }

        recordUsageLog("LOCAL", "local-restaurant-rules", "BLOCKED", 0, 0, 0, 0L, msg, "Off-topic query (Local Assistant)");
        return buildBlockedReply(msg, "FALLBACK", "Local Assistant", null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminAiSettings() {
        AiChatConfig dbConfig = aiChatConfigRepository.findFirstByOrderByConfigIdAsc().orElse(null);
        ResolvedSettings resolved = resolveSettings(dbConfig);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long totalPrompt = aiChatUsageLogRepository.sumPromptTokens();
        long totalOutput = aiChatUsageLogRepository.sumOutputTokens();
        long totalTokens = aiChatUsageLogRepository.sumTotalTokens();
        long todayTokens = aiChatUsageLogRepository.sumTotalTokensSince(startOfToday);
        long successRequests = aiChatUsageLogRepository.countByStatus("SUCCESS");
        long fallbackRequests = aiChatUsageLogRepository.countByStatus("FALLBACK");
        long blockedRequests = aiChatUsageLogRepository.countByStatus("BLOCKED");
        long errorRequests = aiChatUsageLogRepository.countByStatus("ERROR");

        List<Map<String, Object>> recentLogs = aiChatUsageLogRepository.findTop50ByOrderByCreatedAtDescLogIdDesc()
                .stream()
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("logId", l.getLogId());
                    m.put("provider", l.getProvider());
                    m.put("modelName", l.getModelName());
                    m.put("status", l.getStatus());
                    m.put("promptTokens", l.getPromptTokens());
                    m.put("outputTokens", l.getOutputTokens());
                    m.put("totalTokens", l.getTotalTokens());
                    m.put("latencyMs", l.getLatencyMs());
                    m.put("userMessagePreview", l.getUserMessagePreview());
                    m.put("errorMessage", l.getErrorMessage());
                    m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().format(DT_FMT) : "");
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRequests", successRequests + fallbackRequests + blockedRequests + errorRequests);
        summary.put("successRequests", successRequests);
        summary.put("fallbackRequests", fallbackRequests);
        summary.put("blockedRequests", blockedRequests);
        summary.put("errorRequests", errorRequests);
        summary.put("totalPromptTokens", totalPrompt);
        summary.put("totalOutputTokens", totalOutput);
        summary.put("totalTokens", totalTokens);
        summary.put("todayTokens", todayTokens);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "GEMINI");
        result.put("aiEnabled", resolved.aiEnabled());
        result.put("modelName", resolved.modelName());
        result.put("temperature", resolved.temperature());
        result.put("maxOutputTokens", resolved.maxOutputTokens());
        result.put("customSystemPrompt", resolved.customSystemPrompt() != null ? resolved.customSystemPrompt() : "");
        result.put("hasToken", resolved.hasToken());
        result.put("hasDbToken", resolved.hasDbToken());
        result.put("hasEnvToken", resolved.hasEnvToken());
        result.put("tokenSource", resolved.tokenSource());
        result.put("maskedToken", maskToken(resolved.apiToken()));
        result.put("updatedBy", dbConfig != null ? dbConfig.getUpdatedBy() : null);
        result.put("updatedAt", dbConfig != null && dbConfig.getUpdatedAt() != null ? dbConfig.getUpdatedAt().format(DT_FMT) : null);
        result.put("usageSummary", summary);
        result.put("recentLogs", recentLogs);
        return result;
    }

    @Transactional
    public Map<String, Object> saveAdminAiSettings(Map<String, Object> payload, String updatedBy) {
        if (payload == null) {
            throw new IllegalArgumentException("AI settings payload is required.");
        }

        AiChatConfig config = aiChatConfigRepository.findFirstByOrderByConfigIdAsc()
                .orElseGet(() -> AiChatConfig.builder().provider("GEMINI").build());

        if (payload.containsKey("aiEnabled")) {
            config.setAiEnabled(Boolean.parseBoolean(String.valueOf(payload.get("aiEnabled"))));
        }

        if (payload.get("modelName") != null) {
            String model = String.valueOf(payload.get("modelName")).trim();
            if (!model.isEmpty()) {
                config.setModelName(model);
            }
        }

        if (payload.get("temperature") != null && !String.valueOf(payload.get("temperature")).isBlank()) {
            double temp = Double.parseDouble(String.valueOf(payload.get("temperature")));
            config.setTemperature(Math.max(0.0, Math.min(2.0, temp)));
        }

        if (payload.get("maxOutputTokens") != null && !String.valueOf(payload.get("maxOutputTokens")).isBlank()) {
            int maxTok = Integer.parseInt(String.valueOf(payload.get("maxOutputTokens")));
            config.setMaxOutputTokens(Math.max(64, Math.min(8192, maxTok)));
        }

        if (payload.containsKey("customSystemPrompt")) {
            Object promptObj = payload.get("customSystemPrompt");
            config.setCustomSystemPrompt(promptObj != null ? String.valueOf(promptObj).trim() : null);
        }

        boolean clearToken = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("clearToken", false)));
        if (clearToken) {
            config.setApiToken(null);
        } else if (payload.get("apiToken") != null) {
            String incomingToken = normalizeApiToken(String.valueOf(payload.get("apiToken")));
            if (!incomingToken.isEmpty() && !incomingToken.contains("••••")) {
                config.setApiToken(incomingToken);
            }
        }

        config.setUpdatedBy(updatedBy != null && !updatedBy.isBlank() ? updatedBy : "Savora Admin");
        aiChatConfigRepository.save(config);
        return getAdminAiSettings();
    }

    @Transactional
    public Map<String, Object> testGeminiConnection(String overrideToken, String overrideModel) {
        ResolvedSettings settings = resolveSettings();
        String cleanedOverride = normalizeApiToken(overrideToken);
        String tokenToUse = (!cleanedOverride.isEmpty() && !cleanedOverride.contains("••••"))
                ? cleanedOverride
                : normalizeApiToken(settings.apiToken());
        String modelToUse = (overrideModel != null && !overrideModel.isBlank())
                ? overrideModel.trim()
                : settings.modelName();

        if (tokenToUse == null || tokenToUse.isBlank()) {
            throw new IllegalArgumentException("No Gemini API token found. Please paste a valid AQ... / AIza... token or set GEMINI_API_KEY.");
        }

        GeminiApiClient.GeminiResult res = geminiApiClient.generateContent(
                tokenToUse,
                modelToUse,
                "You are a connectivity test helper. Keep your reply under 20 words.",
                List.of(new GeminiApiClient.ChatTurn("user", "Say hello and confirm Savora Gemini AI is ready.")),
                0.2,
                128
        );

        recordUsageLog(
                "GEMINI",
                modelToUse,
                res.success() ? "SUCCESS" : "ERROR",
                res.promptTokens(),
                res.outputTokens(),
                res.totalTokens(),
                res.latencyMs(),
                "[Admin Token Test]",
                res.errorMessage()
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connected", res.success());
        out.put("model", modelToUse);
        out.put("reply", res.text());
        out.put("latencyMs", res.latencyMs());
        out.put("promptTokens", res.promptTokens());
        out.put("outputTokens", res.outputTokens());
        out.put("totalTokens", res.totalTokens());
        out.put("errorMessage", res.errorMessage());
        return out;
    }

    @Transactional
    public void clearUsageLogs() {
        aiChatUsageLogRepository.deleteAll();
    }

    private ResolvedSettings resolveSettings() {
        AiChatConfig dbConfig = aiChatConfigRepository.findFirstByOrderByConfigIdAsc().orElse(null);
        return resolveSettings(dbConfig);
    }

    private ResolvedSettings resolveSettings(AiChatConfig dbConfig) {
        String dbToken = dbConfig != null ? normalizeApiToken(dbConfig.getApiToken()) : "";
        String envToken = normalizeApiToken(envApiKey);

        boolean hasDbToken = !dbToken.isEmpty();
        boolean hasEnvToken = !envToken.isEmpty();

        String activeToken = hasDbToken ? dbToken : envToken;
        String tokenSource = hasDbToken ? "ADMIN_DB" : (hasEnvToken ? "ENV_CONFIG" : "NONE");

        boolean enabled = dbConfig != null && dbConfig.getAiEnabled() != null
                ? dbConfig.getAiEnabled()
                : defaultEnabled;

        String model = (dbConfig != null && dbConfig.getModelName() != null && !dbConfig.getModelName().isBlank())
                ? dbConfig.getModelName().trim()
                : ((defaultModel != null && !defaultModel.isBlank()) ? defaultModel.trim() : DEFAULT_MODEL);

        double temperature = (dbConfig != null && dbConfig.getTemperature() != null)
                ? dbConfig.getTemperature()
                : 0.7;

        int maxTokens = (dbConfig != null && dbConfig.getMaxOutputTokens() != null)
                ? dbConfig.getMaxOutputTokens()
                : 2048;

        String customPrompt = dbConfig != null ? dbConfig.getCustomSystemPrompt() : null;

        return new ResolvedSettings(
                enabled,
                activeToken,
                tokenSource,
                model,
                temperature,
                maxTokens,
                customPrompt,
                hasDbToken,
                hasEnvToken
        );
    }

    private String buildGeminiSystemPrompt(
            List<Dish> dishes,
            List<DiningTable> tables,
            List<Voucher> vouchers,
            String customSystemPrompt
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the official AI Concierge for the Savora Fine Dining & Lounge website.\n");
        sb.append("STRICT DOMAIN POLICY (MANDATORY):\n");
        sb.append("1. ONLY answer questions directly related to the Savora Fine Dining & Lounge website and restaurant services: menu dishes, ingredients, prices/discounts, live dining tables, floor zones, online table reservations, promotional vouchers, Table QR ordering (/qr/table/{tableCode}), kitchen & bill status, payment methods (VietQR/Cash), opening hours, address, concierge contact & feedback (/contact), and member accounts/portals (Login, Google Sign-In, My Bookings, Profile).\n");
        sb.append("2. Simple greetings or introductions (e.g., 'hello', 'xin chào', 'tôi là Long bạn là ai') are allowed: greet the guest warmly as Savora AI Assistant and briefly mention how you can help on the Savora website.\n");
        sb.append("3. STRICTLY REFUSE any question unrelated to Savora Restaurant or this website (such as programming, math, homework, weather, politics, sports, crypto, celebrities, general trivia, other restaurants, or writing stories/poems).\n");
        sb.append("4. Whenever you refuse an off-topic question, start your response with the exact marker ").append(OUT_OF_SCOPE_MARKER)
                .append(" followed by a polite explanation in the same language the user used, stating that you only support questions about the Savora Restaurant website.\n");
        sb.append("5. Always reply in the same language the user writes in (Vietnamese or English).\n");
        sb.append("6. Base every Savora fact strictly on the LIVE DATABASE SNAPSHOT below — never invent prices, vouchers, dishes, or table codes.\n\n");

        sb.append("=== SAVORA RESTAURANT LIVE INFO ===\n");
        sb.append("Name: Savora Fine Dining & Lounge\n");
        sb.append("Address: Hoa Lac Hi-Tech Park, Thach That, Hanoi\n");
        sb.append("Opening Hours: 10:00 AM - 10:30 PM Daily (Online table booking slots: 10:30 - 21:00; 120 mins dining + 10 mins cleaning turnaround)\n");
        sb.append("Hotline: 1900 6868 | Email: concierge@savora.vn\n");
        sb.append("Website sections: Home (/home), Deals (/home#deals), Floor Map (/home#tables-section), Menu (/home#menu), Contact & Feedback (/contact), My Bookings (/customer/reservations), QR Table Ordering (/qr/table/{tableCode}).\n\n");

        sb.append("Available Menu Dishes (" + dishes.size() + "):\n");
        for (Dish d : dishes) {
            BigDecimal eff = d.getDiscountPrice() != null ? d.getDiscountPrice() : d.getPrice();
            sb.append("- ").append(d.getDishName())
                    .append(" [").append(d.getCategory() != null ? d.getCategory().getCategoryName() : "Main").append("]")
                    .append(Boolean.TRUE.equals(d.getIsSignature()) ? " (Signature)" : "")
                    .append(": ").append(fmtVnd(eff));
            if (d.getDiscountPrice() != null && d.getPrice() != null) {
                sb.append(" (original ").append(fmtVnd(d.getPrice())).append(")");
            }
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                sb.append(" — ").append(d.getDescription());
            }
            sb.append("\n");
        }

        sb.append("\nDining Tables (" + tables.size() + "):\n");
        for (DiningTable t : tables) {
            sb.append("- ").append(t.getTableCode()).append(" (").append(t.getTableName()).append(")")
                    .append(", Area: ").append(t.getArea())
                    .append(", Max seats: ").append(t.getCapacity())
                    .append(", Status: ").append(t.getStatus()).append("\n");
        }

        sb.append("\nActive Vouchers (" + vouchers.size() + "):\n");
        for (Voucher v : vouchers) {
            sb.append("- ").append(v.getVoucherCode())
                    .append(" (-").append(v.getDiscountPercent()).append("%): ")
                    .append(v.getDescription() != null ? v.getDescription() : "")
                    .append("\n");
        }

        if (customSystemPrompt != null && !customSystemPrompt.isBlank()) {
            sb.append("\n=== EXTRA ADMIN INSTRUCTIONS ===\n");
            sb.append(customSystemPrompt.trim()).append("\n");
        }

        return sb.toString();
    }

    private List<GeminiApiClient.ChatTurn> buildConversationTurns(List<Map<String, String>> history, String currentMessage) {
        List<GeminiApiClient.ChatTurn> turns = new ArrayList<>();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            for (int i = start; i < history.size(); i++) {
                Map<String, String> item = history.get(i);
                if (item == null) continue;
                String text = item.get("text");
                if (text == null || text.isBlank()) continue;
                String rawRole = item.get("role");
                String role = ("bot".equalsIgnoreCase(rawRole) || "model".equalsIgnoreCase(rawRole) || "assistant".equalsIgnoreCase(rawRole))
                        ? "model"
                        : "user";
                turns.add(new GeminiApiClient.ChatTurn(role, text.trim()));
            }
        }
        turns.add(new GeminiApiClient.ChatTurn("user", currentMessage));
        return turns;
    }

    private List<Map<String, String>> detectRelevantLinks(String userMsg, String aiReply, List<Dish> dishes) {
        String combined = ((userMsg != null ? userMsg : "") + " " + (aiReply != null ? aiReply : "")).toLowerCase(Locale.ROOT);
        boolean mentionsDish = (dishes != null && dishes.stream()
                .anyMatch(d -> d.getDishName() != null && combined.contains(d.getDishName().toLowerCase(Locale.ROOT))))
                || containsAny(combined, "menu", "dish", "món", "thực đơn", "steak", "wagyu", "tenderloin",
                        "lobster", "salmon", "hotpot", "lẩu", "bít tết", "cá hồi", "tôm hùm", "ribs", "sườn", "dessert", "mocktail", "vnd");
        boolean mentionsTable = containsAny(combined, "table", "bàn", "đặt bàn", "book", "reserve", "reservation", "floor", "vip", "terrace", "qr");
        boolean mentionsVoucher = containsAny(combined, "voucher", "deal", "giảm giá", "khuyến mãi", "promo", "savora10", "welcome50", "vipparty20");
        boolean mentionsContact = containsAny(combined, "contact", "feedback", "liên hệ", "góp ý", "hotline", "1900 6868", "hoa lac", "hòa lạc");
        boolean mentionsAccount = containsAny(combined, "login", "sign in", "register", "đăng nhập", "đăng ký", "tài khoản", "profile", "my bookings");
        boolean mentionsSavora = containsAny(combined, "savora", "nhà hàng", "restaurant");

        if (!mentionsDish && !mentionsTable && !mentionsVoucher && !mentionsContact && !mentionsAccount && !mentionsSavora) {
            return List.of();
        }
        List<Map<String, String>> links = new ArrayList<>();
        if (mentionsDish) {
            links.add(link("🍽️ View Menu", "/home#menu"));
        }
        if (mentionsTable) {
            links.add(link("🪑 Book a Table", "/home#tables-section"));
        }
        if (mentionsVoucher) {
            links.add(link("🎁 Voucher Deals", "/home#deals"));
        }
        if (mentionsContact) {
            links.add(link("✉️ Contact & Feedback", "/contact"));
        }
        if (mentionsAccount) {
            links.add(link("📅 My Bookings", "/customer/reservations"));
        }
        if (links.isEmpty()) {
            links.add(link("🪑 Book a Table", "/home#tables-section"));
            links.add(link("🍽️ View Menu", "/home#menu"));
        }
        return links;
    }

    private Map<String, Object> tryLocalRestaurantReply(
            String msg,
            List<Dish> dishes,
            List<DiningTable> tables,
            List<Voucher> vouchers
    ) {
        String lower = msg.toLowerCase(Locale.ROOT);

        // 1. Specific dish in database
        for (Dish d : dishes) {
            if (matchesDishQuery(lower, d)) {
                BigDecimal effPrice = d.getDiscountPrice() != null ? d.getDiscountPrice() : d.getPrice();
                String priceStr = fmtVnd(effPrice) + (d.getDiscountPrice() != null ? " (original " + fmtVnd(d.getPrice()) + ")" : "");
                String sig = Boolean.TRUE.equals(d.getIsSignature()) ? "★ Signature Dish • " : "";
                String reply = "🍽️ **" + d.getDishName() + "** (" + sig + (d.getCategory() != null ? d.getCategory().getCategoryName() : "Main Menu") + ")\n"
                        + "• **Price:** " + priceStr + "\n"
                        + "• **Prep Time:** ~" + (d.getPreparationTimeMinutes() != null ? d.getPreparationTimeMinutes() : 15) + " mins\n"
                        + "• **Details:** " + (d.getDescription() != null ? d.getDescription() : "Prepared fresh daily by our Executive Chef.");
                return buildReply(
                        false,
                        reply,
                        List.of(
                                link("🍽️ View on Signature Menu", "/home#menu"),
                                link("🪑 Reserve a Table to Dine", "/home#tables-section"),
                                link("🎁 Apply Voucher Deal", "/home#deals")
                        ),
                        "FALLBACK",
                        "Local Assistant",
                        null
                );
            }
        }

        // 2. Vouchers / Promotions / Discounts
        if (containsAny(lower, "voucher", "deal", "discount", "promo", "offer", "coupon", "sale", "khuyến mãi", "giảm giá", "mã")) {
            String voucherLines = vouchers.isEmpty()
                    ? "Currently no active promotional vouchers."
                    : vouchers.stream()
                    .map(v -> "• **" + v.getVoucherCode() + " (-" + v.getDiscountPercent() + "%)**: " + v.getDescription())
                    .collect(Collectors.joining("\n"));
            String reply = "🎁 **Active Savora Promotional Vouchers:**\n" + voucherLines
                    + "\n\nYou can select any of these voucher codes directly when booking a table online or when ordering via Table QR!";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🎁 Claim Promotional Deals", "/home#deals"),
                            link("🪑 Book Table with Deal", "/home#tables-section")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        // 3. Table Availability / Floor Map / VIP / Zones
        if (containsAny(lower, "table", "seat", "available", "floor", "vip", "terrace", "garden", "zone", "capacity", "room", "bàn", "chỗ")) {
            List<DiningTable> availTables = tables.stream()
                    .filter(t -> t.getStatus() == TableStatus.AVAILABLE)
                    .collect(Collectors.toList());
            String availSummary = availTables.isEmpty()
                    ? "All tables are currently occupied or reserved for the current slot, but you can book later time slots today!"
                    : availTables.stream()
                    .map(t -> "**" + t.getTableCode() + "** (" + t.getArea() + ", max " + t.getCapacity() + " seats)")
                    .collect(Collectors.joining(", "));

            String reply = "🪑 **Live Dining Table Status (" + availTables.size() + "/" + tables.size() + " Currently Available):**\n"
                    + "• **Available Right Now:** " + availSummary + "\n"
                    + "• **Zones:** 1st Floor (Main Hall, 2–8 seats), 2nd Floor (Garden Terrace, 6–8 seats), and VIP Private Suites (12–16 seats).\n"
                    + "• **Turnaround Rule:** Each booking holds a table for **120 minutes dining + 10 minutes sanitizing (130 mins total)**.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🪑 View Live Floor Map", "/home#tables-section"),
                            link("📅 My Table Bookings", "/customer/reservations")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        // 4. How to Book / Reservation Policy
        if (containsAny(lower, "book", "reserve", "reservation", "slot", "cancel", "party size", "đặt bàn", "đặt chỗ")) {
            String reply = "📅 **How to Reserve a Table Online at Savora:**\n"
                    + "1. Go to our **Book a Table** form on the Home page and select **Adults**, **Children** (total guests must fit table seat capacity), **Arrival Date**, and **Time Slot (10:30 – 21:00)**.\n"
                    + "2. Pick an **Available Table** on the Floor Map and attach a **Promotional Voucher** (up to 20% OFF).\n"
                    + "3. Sign in and click **Reserve Table Now (Free)**. You can view or cancel your booking anytime in **My Bookings**.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🪑 Book a Table Now", "/home#tables-section"),
                            link("📅 My Bookings History", "/customer/reservations"),
                            link("🔐 Sign In", "/login")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        // 5. Menu / Dishes / Food / Drinks
        if (containsAny(lower, "menu", "dish", "food", "eat", "drink", "steak", "seafood", "salad", "dessert", "hotpot", "signature", "price", "món", "thực đơn", "ăn", "uống", "giá")) {
            String topDishes = dishes.stream().limit(5)
                    .map(d -> "• **" + d.getDishName() + "** — " + fmtVnd(d.getDiscountPrice() != null ? d.getDiscountPrice() : d.getPrice())
                            + (Boolean.TRUE.equals(d.getIsSignature()) ? " *(★ Signature)*" : ""))
                    .collect(Collectors.joining("\n"));
            String reply = "🍽️ **Savora Chef’s Signature Menu (" + dishes.size() + " Available Dishes):**\n"
                    + topDishes + "\n\n"
                    + "Our menu spans *Starters & Salads*, *Prime Steaks & Seafood*, *Hotpot & Grill*, *Artisan Desserts*, and *Wines & Cocktails*.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🍽️ Browse Full Menu", "/home#menu"),
                            link("🪑 Reserve a Table", "/home#tables-section")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        // 6. Table QR Ordering / Payment
        if (containsAny(lower, "qr", "order", "kitchen", "pay", "payment", "bill", "invoice", "vietqr", "mbbank", "cash", "thanh toán", "hóa đơn")) {
            String reply = "📱 **Table QR Ordering & Checkout Workflow:**\n"
                    + "• **At the Table:** Scan your table's unique QR code (`/qr/table/{tableCode}`) to order dishes directly to the Chef Kitchen Display System without needing to sign in.\n"
                    + "• **Live Kitchen Status:** Track every dish in real time from **PENDING → COOKING → READY → SERVED**.\n"
                    + "• **Billing & Payment:** When finished dining, our Waiter generates your official tax invoice with **VietQR (MBBank)** or **Cash** payment.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🍽️ View Signature Menu", "/home#menu"),
                            link("🪑 Book a Dining Table", "/home#tables-section")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        // 7. Contact / Feedback / Location / Hours
        if (containsAny(lower, "contact", "feedback", "email", "where", "location", "address", "hour", "open", "close", "phone", "hotline", "địa chỉ", "liên hệ", "mở cửa", "giờ", "góp ý")) {
            String reply = "📍 **Savora Fine Dining — Location, Hours & Guest Feedback:**\n"
                    + "• **Address:** Hoa Lac Hi-Tech Park, Thach That, Hanoi\n"
                    + "• **Opening Hours:** 10:00 AM – 10:30 PM Daily (Online booking slots: 10:30 – 21:00)\n"
                    + "• **Concierge Hotline:** 1900 6868 • **Email:** concierge@savora.vn\n"
                    + "• **Guest Feedback:** Visit our **Contact & Feedback** page to send us your thoughts and receive an instant confirmation email!";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("✉️ Open Contact & Feedback Form", "/contact"),
                            link("🪑 Book a Table", "/home#tables-section")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        // 8. Account / Profile / Sign In
        if (containsAny(lower, "account", "profile", "login", "sign in", "register", "sign up", "password", "google", "đăng nhập", "đăng ký", "tài khoản")) {
            String reply = "👤 **Savora Member Account & Profile:**\n"
                    + "• You can sign in using your **Email & Password** or **Google** account to reserve tables online.\n"
                    + "• Once signed in, use **My Bookings** to manage reservations and **Profile** to update your name, phone number, gender, and address.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("👤 My Account Profile", "/customer/profile"),
                            link("📅 My Bookings", "/customer/reservations"),
                            link("🔐 Sign In", "/login"),
                            link("📝 Sign Up", "/register")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        // 9. Greeting / Savora Overview
        if (containsAny(lower, "hi", "hello", "hey", "xin chào", "chào", "help", "savora", "restaurant", "nhà hàng")) {
            String reply = "👋 Welcome to **Savora Fine Dining & Lounge AI Assistant**!\n"
                    + "I can help you with:\n"
                    + "• 🍽️ **Signature Menu & Dish Prices** (" + dishes.size() + " dishes available)\n"
                    + "• 🪑 **Real-Time Table Availability & Online Booking** (" + tables.size() + " tables across 3 zones)\n"
                    + "• 🎁 **Promotional Vouchers** (up to 20% OFF)\n"
                    + "• 📍 **Location, Opening Hours & Guest Feedback**";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🪑 Book a Table", "/home#tables-section"),
                            link("🍽️ Signature Menu", "/home#menu"),
                            link("🎁 Voucher Deals", "/home#deals"),
                            link("✉️ Contact & Feedback", "/contact")
                    ),
                    "FALLBACK",
                    "Local Assistant",
                    null
            );
        }

        return null;
    }

    private void recordUsageLog(
            String provider,
            String modelName,
            String status,
            int promptTokens,
            int outputTokens,
            int totalTokens,
            long latencyMs,
            String message,
            String errorMessage
    ) {
        try {
            aiChatUsageLogRepository.save(AiChatUsageLog.builder()
                    .provider(provider)
                    .modelName(modelName)
                    .status(status)
                    .promptTokens(promptTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .latencyMs(latencyMs)
                    .userMessagePreview(safeShort(message, 240))
                    .errorMessage(errorMessage != null ? safeShort(errorMessage, 480) : null)
                    .build());
        } catch (Exception ignored) {
        }
    }

    private boolean isExplicitlyOffTopic(String msg, List<Dish> dishes) {
        if (msg == null || msg.isBlank()) return false;
        String lower = msg.toLowerCase(Locale.ROOT);
        boolean mentionsSavora = containsAny(lower,
                "savora", "nhà hàng", "restaurant", "menu", "thực đơn", "món", "dish",
                "bàn", "table", "đặt bàn", "book", "reserve", "voucher", "deal", "khuyến mãi",
                "giảm giá", "qr", "thanh toán", "hóa đơn", "vietqr", "liên hệ", "contact", "feedback", "góp ý")
                || (dishes != null && dishes.stream()
                .anyMatch(d -> d.getDishName() != null && lower.contains(d.getDishName().toLowerCase(Locale.ROOT))));
        if (mentionsSavora) {
            return false;
        }
        if (MATH_EXPR_PATTERN.matcher(lower).matches()) {
            return true;
        }
        for (String kw : OFF_TOPIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, String>> defaultSavoraLinks() {
        return List.of(
                link("🪑 Book a Table", "/home#tables-section"),
                link("🍽️ View Menu", "/home#menu"),
                link("🎁 Promotional Deals", "/home#deals"),
                link("✉️ Contact & Feedback", "/contact")
        );
    }

    private Map<String, Object> buildBlockedReply(String userMsg, String mode, String model, Map<String, Object> usage) {
        boolean isVietnamese = looksVietnamese(userMsg);
        String msg = isVietnamese
                ? "🚫 **Câu hỏi ngoài phạm vi hỗ trợ**\n"
                + "Tôi là **Trợ lý AI của Nhà hàng Savora** và chỉ được cấu hình để trả lời **các thông tin liên quan đến website Savora** (thực đơn, giá món, tình trạng bàn trống, đặt bàn trực tuyến, voucher ưu đãi, gọi món QR tại bàn, giờ mở cửa và liên hệ/góp ý).\n\n"
                + "Tôi không trả lời các chủ đề bên ngoài nhà hàng. Anh/chị vui lòng chọn nhanh các mục của Savora bên dưới:"
                : "🚫 **Out-of-Scope Question**\n"
                + "I am the **Savora Fine Dining AI Assistant**, and I only answer questions related to the **Savora Restaurant website** (menu dishes, prices, live table availability, online reservations, vouchers, Table QR ordering, opening hours, and guest feedback).\n\n"
                + "I cannot answer unrelated questions. Please explore Savora using the links below:";
        return buildReply(true, msg, defaultSavoraLinks(), mode, model, usage);
    }

    private boolean matchesDishQuery(String lower, Dish d) {
        if (d.getDishName() == null) return false;
        String nameLower = d.getDishName().toLowerCase(Locale.ROOT);
        if (lower.contains(nameLower)) return true;
        if (nameLower.contains("salmon") && containsAny(lower, "salmon", "cá hồi")) return true;
        if (nameLower.contains("tenderloin") && containsAny(lower, "tenderloin", "steak", "beef", "bít tết", "bò")) return true;
        if (nameLower.contains("lobster") && containsAny(lower, "lobster", "tôm hùm")) return true;
        if (nameLower.contains("hotpot") && containsAny(lower, "hotpot", "hot pot", "lẩu")) return true;
        if (nameLower.contains("pumpkin") && containsAny(lower, "pumpkin", "truffle soup", "bí đỏ")) return true;
        if (nameLower.contains("crème brûlée") && containsAny(lower, "crème brûlée", "creme brulee", "vanilla")) return true;
        if (nameLower.contains("mocktail") && containsAny(lower, "mocktail", "hibiscus")) return true;
        if (nameLower.contains("ribs") && containsAny(lower, "ribs", "pork", "iberico", "sườn")) return true;
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private boolean looksVietnamese(String text) {
        if (text == null) return false;
        return text.chars().anyMatch(c -> c > 127)
                || containsAny(text.toLowerCase(Locale.ROOT), "anh", "em", "minh", "ban", "nha hang", "mon an", "dat ban", "giup", "khong", "duoc");
    }

    private Map<String, Object> buildReply(
            boolean blocked,
            String answer,
            List<Map<String, String>> links,
            String mode,
            String model,
            Map<String, Object> usage
    ) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("blocked", blocked);
        res.put("answer", answer);
        res.put("links", links);
        res.put("mode", mode);
        res.put("model", model);
        if (usage != null) {
            res.put("usage", usage);
        }
        return res;
    }

    private Map<String, String> link(String label, String url) {
        return Map.of("label", label, "url", url);
    }

    private String fmtVnd(BigDecimal amount) {
        if (amount == null) return "0 VND";
        return NumberFormat.getNumberInstance(Locale.US).format(amount.longValue()) + " VND";
    }

    private String normalizeApiToken(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        if (t.startsWith("Bearer ") || t.startsWith("bearer ")) {
            t = t.substring(7).trim();
        }
        int eqIdx = t.indexOf('=');
        if (eqIdx > 0 && eqIdx < 32) {
            String keyPart = t.substring(0, eqIdx).trim().toLowerCase(Locale.ROOT);
            if (keyPart.contains("api-key") || keyPart.contains("api_key") || keyPart.contains("token")) {
                t = t.substring(eqIdx + 1).trim();
            }
        }
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            if (t.length() >= 2) {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) return "";
        String t = token.trim();
        if (t.length() <= 8) return "••••" + t.substring(Math.max(0, t.length() - 2));
        return t.substring(0, 4) + "••••••" + t.substring(t.length() - 4);
    }

    private String safeShort(String text, int maxLen) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxLen ? clean : clean.substring(0, maxLen - 1) + "…";
    }
}
