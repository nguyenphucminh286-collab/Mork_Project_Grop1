package vn.edu.fpt.savora.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * HTTP client for Google Gemini REST API (supports both AI Studio AIza.../AQ. and Vertex Express AQ. keys).
 */
@Slf4j
@Component
public class GeminiApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);
    private static final String DEFAULT_AI_STUDIO_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEFAULT_VERTEX_BASE = "https://aiplatform.googleapis.com/v1/publishers/google";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private final String vertexBaseUrl;

    public GeminiApiClient(String baseUrl) {
        this(baseUrl, DEFAULT_VERTEX_BASE);
    }

    @Autowired
    public GeminiApiClient(
            @Value("${ai.gemini.base-url:" + DEFAULT_AI_STUDIO_BASE + "}") String baseUrl,
            @Value("${ai.gemini.vertex-base-url:" + DEFAULT_VERTEX_BASE + "}") String vertexBaseUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.jsonMapper = JsonMapper.shared();
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl.replaceAll("/+$", "")
                : DEFAULT_AI_STUDIO_BASE;
        this.vertexBaseUrl = (vertexBaseUrl != null && !vertexBaseUrl.isBlank())
                ? vertexBaseUrl.replaceAll("/+$", "")
                : DEFAULT_VERTEX_BASE;
    }

    public record ChatTurn(String role, String text) {}

    public record GeminiResult(
            boolean success,
            String text,
            int promptTokens,
            int outputTokens,
            int totalTokens,
            long latencyMs,
            String errorMessage,
            int httpStatus
    ) {}

    public GeminiResult generateContent(
            String apiToken,
            String modelName,
            String systemInstruction,
            List<ChatTurn> turns,
            Double temperature,
            Integer maxOutputTokens
    ) {
        long startNs = System.nanoTime();
        if (apiToken == null || apiToken.isBlank()) {
            return new GeminiResult(false, null, 0, 0, 0, 0L, "Missing Gemini API token.", 0);
        }

        String trimmedToken = apiToken.trim();
        String resolvedModel = (modelName != null && !modelName.isBlank())
                ? modelName.trim()
                : "gemini-3.8-flash";

        try {
            Map<String, Object> payload = new LinkedHashMap<>();

            if (systemInstruction != null && !systemInstruction.isBlank()) {
                payload.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ));
            }

            List<Map<String, Object>> contents = new ArrayList<>();
            if (turns != null) {
                for (ChatTurn turn : turns) {
                    if (turn == null || turn.text() == null || turn.text().isBlank()) continue;
                    String role = "model".equalsIgnoreCase(turn.role()) ? "model" : "user";
                    contents.add(Map.of(
                            "role", role,
                            "parts", List.of(Map.of("text", turn.text()))
                    ));
                }
            }
            payload.put("contents", contents);

            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("temperature", temperature != null ? Math.max(0.0, Math.min(2.0, temperature)) : 0.7);
            generationConfig.put("maxOutputTokens", maxOutputTokens != null ? Math.max(64, Math.min(8192, maxOutputTokens)) : 2048);
            payload.put("generationConfig", generationConfig);

            String encodedModel = URLEncoder.encode(resolvedModel, StandardCharsets.UTF_8);
            String requestBody = jsonMapper.writeValueAsString(payload);

            // Try primary AI Studio endpoint first; if an AQ. key belongs to Vertex Express Mode, retry Vertex publisher endpoint
            GeminiResult primary = callEndpoint(baseUrl + "/models/" + encodedModel + ":generateContent", trimmedToken, resolvedModel, requestBody, startNs);
            if (primary.success()) {
                return primary;
            }

            boolean shouldTryVertexFallback = trimmedToken.startsWith("AQ.")
                    && !baseUrl.equals(vertexBaseUrl)
                    && (primary.httpStatus() == 400 || primary.httpStatus() == 401 || primary.httpStatus() == 403 || primary.httpStatus() == 404);
            if (shouldTryVertexFallback) {
                GeminiResult vertexResult = callEndpoint(vertexBaseUrl + "/models/" + encodedModel + ":generateContent", trimmedToken, resolvedModel, requestBody, startNs);
                if (vertexResult.success()) {
                    return vertexResult;
                }
                return new GeminiResult(
                        false,
                        null,
                        0,
                        0,
                        0,
                        vertexResult.latencyMs(),
                        primary.errorMessage() + " | Vertex fallback: " + vertexResult.errorMessage(),
                        vertexResult.httpStatus()
                );
            }

            return primary;
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Gemini API call failed (model={}): {}", resolvedModel, msg);
            return new GeminiResult(false, null, 0, 0, 0, latencyMs, msg, 0);
        }
    }

    private GeminiResult callEndpoint(String endpoint, String apiToken, String resolvedModel, String requestBody, long startNs) {
        try {
            record HttpOutcome(int statusCode, String body) {}
            HttpOutcome outcome = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", apiToken)
                    .body(requestBody)
                    .exchange((req, resp) -> {
                        int code = resp.getStatusCode().value();
                        String raw = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return new HttpOutcome(code, raw);
                    });

            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            if (outcome == null) {
                return new GeminiResult(false, null, 0, 0, 0, latencyMs, "Empty response from Gemini API.", 0);
            }

            JsonNode root = (outcome.body() != null && !outcome.body().isBlank())
                    ? jsonMapper.readTree(outcome.body())
                    : jsonMapper.createObjectNode();

            if (outcome.statusCode() < 200 || outcome.statusCode() >= 300) {
                String apiErr = root.path("error").path("message").asString("");
                if (apiErr.isBlank()) {
                    apiErr = "Gemini API returned HTTP " + outcome.statusCode();
                }
                log.warn("Gemini API error (HTTP {}, model={}, endpoint={}): {}", outcome.statusCode(), resolvedModel, endpoint, apiErr);
                return new GeminiResult(false, null, 0, 0, 0, latencyMs, apiErr, outcome.statusCode());
            }

            JsonNode usageNode = root.path("usageMetadata");
            int promptTokens = usageNode.path("promptTokenCount").asInt(0);
            int outputTokens = usageNode.path("candidatesTokenCount").asInt(0);
            int totalTokens = usageNode.path("totalTokenCount").asInt(promptTokens + outputTokens);

            String replyText = extractReplyText(root);
            if (replyText.isBlank()) {
                String finishReason = root.path("candidates").path(0).path("finishReason").asString("");
                String err = finishReason.isBlank()
                        ? "Gemini returned an empty response."
                        : "Gemini returned no text (finishReason=" + finishReason + ").";
                return new GeminiResult(false, null, promptTokens, outputTokens, totalTokens, latencyMs, err, outcome.statusCode());
            }

            return new GeminiResult(true, replyText, promptTokens, outputTokens, totalTokens, latencyMs, null, outcome.statusCode());
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Gemini API endpoint call failed ({}): {}", endpoint, msg);
            return new GeminiResult(false, null, 0, 0, 0, latencyMs, msg, 0);
        }
    }

    private String extractReplyText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            String text = part.path("text").asString("");
            if (!text.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(text);
            }
        }
        return sb.toString().trim();
    }
}
