package com.sakhtyar.agents.provider;

import java.util.Map;

/**
 * Extension point for future OpenAI, Ollama, Gemini or local model adapters.
 * Core business calculations must not be implemented inside a model provider.
 */
public interface AiModelProvider {

    String providerId();

    boolean available();

    AiModelResponse generate(AiModelRequest request);

    record AiModelRequest(
            String systemPrompt,
            String userMessage,
            Map<String, Object> structuredContext
    ) {
    }

    record AiModelResponse(
            String text,
            Map<String, Object> structuredData,
            String model,
            long inputTokens,
            long outputTokens
    ) {
    }
}
