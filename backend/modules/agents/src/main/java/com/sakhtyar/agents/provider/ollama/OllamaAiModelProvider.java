package com.sakhtyar.agents.provider.ollama;

import com.sakhtyar.agents.provider.AiModelProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Local AI provider backed by Ollama's HTTP API.
 *
 * The model only performs language understanding/generation. Deterministic
 * SakhtYar calculations stay in Java agents/services.
 */
@Component
public class OllamaAiModelProvider implements AiModelProvider {

    private static final System.Logger LOG =
            System.getLogger(OllamaAiModelProvider.class.getName());

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final OllamaProperties properties;
    private final OllamaPerformanceTuner performanceTuner;

    public OllamaAiModelProvider(
            @Qualifier("ollamaRestClient") RestClient client,
            ObjectMapper objectMapper,
            OllamaProperties properties,
            OllamaPerformanceTuner performanceTuner
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.performanceTuner = performanceTuner;
    }

    @Override
    public String providerId() {
        return "ollama";
    }

    @Override
    public String modelId() {
        return properties.getModel();
    }

    @Override
    public boolean available() {
        try {
            String body = client.get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return false;
            }

            JsonNode models = objectMapper.readTree(body).path("models");
            if (!models.isArray()) {
                return false;
            }

            for (JsonNode model : models) {
                String name = model.path("name").asText("");
                String modelName = model.path("model").asText("");
                if (matchesConfiguredModel(name) || matchesConfiguredModel(modelName)) {
                    return true;
                }
            }
            return false;
        } catch (RestClientException | JacksonException ex) {
            return false;
        }
    }

    @Override
    public AiModelResponse generate(AiModelRequest request) {
        Instant startedAt = Instant.now();
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getModel());
            body.put("stream", false);
            body.put("think", properties.isThink());
            body.put("keep_alive", keepAliveValue());
            body.put("format", properties.isStructuredSchema()
                    ? responseSchema()
                    : "json");
            body.put("messages", List.of(
                    Map.of(
                            "role", "system",
                            "content", request.systemPrompt() == null
                                    ? ""
                                    : request.systemPrompt()
                    ),
                    Map.of(
                            "role", "user",
                            "content", buildUserMessage(request)
                    )
            ));
            body.put("options", requestOptions());

            String responseBody = client.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("OLLAMA_EMPTY_RESPONSE: empty response body");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new IllegalStateException(
                        "OLLAMA_EMPTY_RESPONSE: message.content was empty"
                );
            }

            Map<String, Object> structuredData = parseStructuredJson(content);
            logTiming(root, startedAt);

            return new AiModelResponse(
                    content,
                    structuredData,
                    root.path("model").asText(properties.getModel()),
                    root.path("prompt_eval_count").asLong(0L),
                    root.path("eval_count").asLong(0L)
            );
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "OLLAMA_HTTP_ERROR: status=" + ex.getStatusCode().value()
                            + ", body=" + truncate(ex.getResponseBodyAsString(), 800),
                    ex
            );
        } catch (ResourceAccessException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            String code = message.toLowerCase().contains("timed out")
                    || message.toLowerCase().contains("timeout")
                    ? "OLLAMA_TIMEOUT"
                    : "OLLAMA_CONNECTION_FAILED";
            throw new IllegalStateException(
                    code + ": " + properties.getBaseUrl() + " - " + message,
                    ex
            );
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "OLLAMA_CONNECTION_FAILED: " + properties.getBaseUrl()
                            + " - " + safeMessage(ex),
                    ex
            );
        } catch (JacksonException ex) {
            throw new IllegalStateException("OLLAMA_INVALID_JSON: " + safeMessage(ex), ex);
        }
    }

    private Map<String, Object> requestOptions() {
        LinkedHashMap<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", properties.getTemperature());
        options.put("num_ctx", Math.max(512, properties.getContextLength()));
        options.put("num_predict", Math.max(32, properties.getMaxPredictTokens()));
        options.put("num_batch", Math.max(1, properties.getNumBatch()));
        options.put("num_thread", Math.max(1, performanceTuner.numThreads()));
        options.put("use_mmap", properties.isUseMmap());
        return options;
    }

    private Object keepAliveValue() {
        String value = properties.getKeepAlive();
        if (value == null || value.isBlank()) {
            return -1;
        }
        String trimmed = value.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    /**
     * Compact schema for PersianAgent. It constrains the response shape while
     * allowing parameter keys to evolve without a provider code change.
     */
    private Map<String, Object> responseSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "intent", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "PROPERTY_ANALYSIS",
                                        "PARTNERSHIP_ANALYSIS",
                                        "PROPERTY_VALUATION",
                                        "BUILDABILITY_ANALYSIS",
                                        "CONTRACT_REVIEW",
                                        "BUILDER_MATCHING",
                                        "RESEARCH"
                                )
                        ),
                        "normalizedText", Map.of("type", "string"),
                        "parameters", Map.of(
                                "type", "object",
                                "additionalProperties", true
                        )
                ),
                "required", List.of("intent", "parameters"),
                "additionalProperties", false
        );
    }

    private String buildUserMessage(AiModelRequest request) throws JacksonException {
        StringBuilder value = new StringBuilder();
        value.append("\u0645\u062A\u0646 \u06A9\u0627\u0631\u0628\u0631:\n")
                .append(request.userMessage() == null ? "" : request.userMessage());

        if (request.structuredContext() != null && !request.structuredContext().isEmpty()) {
            value.append("\n\n\u062F\u0627\u062F\u0647 \u0645\u0648\u062C\u0648\u062F \u0633\u06CC\u0633\u062A\u0645 (\u0641\u0642\u0637 \u0632\u0645\u06CC\u0646\u0647):\n")
                    .append(objectMapper.writeValueAsString(request.structuredContext()));
        }
        return value.toString();
    }

    private Map<String, Object> parseStructuredJson(String content) throws JacksonException {
        String normalized = stripCodeFence(content.trim());
        Object value = objectMapper.readValue(normalized, Object.class);
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("OLLAMA_INVALID_JSON: root was not an object");
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private String stripCodeFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        int firstNewLine = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        if (firstNewLine >= 0 && lastFence > firstNewLine) {
            return value.substring(firstNewLine + 1, lastFence).trim();
        }
        return value;
    }

    private boolean matchesConfiguredModel(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String configured = properties.getModel();
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return value.equalsIgnoreCase(configured)
                || value.startsWith(configured + ":");
    }

    private void logTiming(JsonNode root, Instant startedAt) {
        long totalMillis = Duration.between(startedAt, Instant.now()).toMillis();
        long loadNs = root.path("load_duration").asLong(0L);
        long promptNs = root.path("prompt_eval_duration").asLong(0L);
        long evalNs = root.path("eval_duration").asLong(0L);
        long promptCount = root.path("prompt_eval_count").asLong(0L);
        long evalCount = root.path("eval_count").asLong(0L);

        LOG.log(System.Logger.Level.INFO,
                "Ollama timing: total={0}ms, load={1}ms, prompt={2}ms/{3} tok, "
                        + "generate={4}ms/{5} tok, threads={6}, ctx={7}",
                totalMillis,
                loadNs / 1_000_000L,
                promptNs / 1_000_000L,
                promptCount,
                evalNs / 1_000_000L,
                evalCount,
                performanceTuner.numThreads(),
                properties.getContextLength());
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength) + "...";
    }
}
