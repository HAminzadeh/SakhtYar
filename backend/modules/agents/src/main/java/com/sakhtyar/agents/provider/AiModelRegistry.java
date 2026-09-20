package com.sakhtyar.agents.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves the configured model provider while keeping agents independent from
 * Ollama/OpenAI/Gemini-specific implementations.
 */
@Service
public class AiModelRegistry {

    private final AiProperties properties;
    private final Map<String, AiModelProvider> providers;

    public AiModelRegistry(
            AiProperties properties,
            List<AiModelProvider> providerList
    ) {
        this.properties = properties;
        LinkedHashMap<String, AiModelProvider> values = new LinkedHashMap<>();
        for (AiModelProvider provider : providerList) {
            values.put(provider.providerId().toLowerCase(), provider);
        }
        this.providers = Map.copyOf(values);
    }

    public Optional<AiModelProvider> activeProvider() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String id = properties.getProvider() == null
                ? ""
                : properties.getProvider().trim().toLowerCase();
        return Optional.ofNullable(providers.get(id));
    }

    public AiRuntimeStatus status() {
        if (!properties.isEnabled()) {
            return new AiRuntimeStatus(false, properties.getProvider(), null, false,
                    "\u0647\u0648\u0634 \u0645\u0635\u0646\u0648\u0639\u06CC \u0645\u062D\u0644\u06CC \u062F\u0631 \u062A\u0646\u0638\u06CC\u0645\u0627\u062A \u063A\u06CC\u0631\u0641\u0639\u0627\u0644 \u0627\u0633\u062A.");
        }

        Optional<AiModelProvider> active = activeProvider();
        if (active.isEmpty()) {
            return new AiRuntimeStatus(true, properties.getProvider(), null, false,
                    "Provider \u062A\u0646\u0638\u06CC\u0645\u200C\u0634\u062F\u0647 \u062F\u0631 Backend \u062B\u0628\u062A \u0646\u0634\u062F\u0647 \u0627\u0633\u062A.");
        }

        AiModelProvider provider = active.get();
        boolean available;
        String message;
        try {
            available = provider.available();
            message = available
                    ? "Provider \u0645\u062D\u0644\u06CC \u0648 \u0645\u062F\u0644 \u062A\u0646\u0638\u06CC\u0645\u200C\u0634\u062F\u0647 \u062F\u0631 \u062F\u0633\u062A\u0631\u0633 \u0647\u0633\u062A\u0646\u062F."
                    : "Provider \u062F\u0631 \u062F\u0633\u062A\u0631\u0633 \u0646\u06CC\u0633\u062A \u06CC\u0627 \u0645\u062F\u0644 \u0647\u0646\u0648\u0632 \u062F\u0627\u0646\u0644\u0648\u062F \u0646\u0634\u062F\u0647 \u0627\u0633\u062A.";
        } catch (RuntimeException ex) {
            available = false;
            message = "\u0627\u062A\u0635\u0627\u0644 \u0628\u0647 Provider \u0645\u062D\u0644\u06CC \u0646\u0627\u0645\u0648\u0641\u0642 \u0628\u0648\u062F: " + safeMessage(ex);
        }

        return new AiRuntimeStatus(
                true,
                provider.providerId(),
                provider.modelId(),
                available,
                message
        );
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message;
    }

    public record AiRuntimeStatus(
            boolean enabled,
            String provider,
            String model,
            boolean available,
            String message
    ) {
    }
}
