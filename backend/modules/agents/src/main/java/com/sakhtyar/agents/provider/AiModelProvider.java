package com.sakhtyar.agents.provider;

import java.util.Map;

/**
 * Contract for AI model providers.
 *
 * Business calculations must stay in Java services/agents. A model provider is
 * only allowed to understand/generate language and return structured data.
 */
public interface AiModelProvider {

    String providerId();

    String modelId();

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
