package com.sakhtyar.agents.agent;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentIntent;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.SakhtyarAgent;
import com.sakhtyar.agents.glossary.PersianGlossaryService;
import com.sakhtyar.agents.provider.AiModelProvider;
import com.sakhtyar.agents.provider.AiModelRegistry;
import com.sakhtyar.agents.provider.PersianAgentPromptService;
import com.sakhtyar.agents.support.AgentValues;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PersianAgent implements SakhtyarAgent {

    private static final Pattern AREA_PATTERN = Pattern.compile(
            "(?:(?:\u0632\u0645\u06CC\u0646|\u0645\u0644\u06A9)\\s*)?(\\d+(?:\\.\\d+)?)\\s*(?:\u0645\u062A\u0631\\s*\u0645\u0631\u0628\u0639|\u0645\u062A\u0631\u06CC)"
    );
    private static final Pattern FRONTAGE_PATTERN = Pattern.compile(
            "\u0628\u0631(?:\u0634|\\s*\u0645\u0644\u06A9)?\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)"
    );
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?:\u062A\u0648|\u062F\u0631)\\s+([\\u0600-\\u06FF\\u200C\\s]+?)(?=\\s+(?:\u062F\u0627\u0631\u0645|\u062F\u0627\u0631\u06CC\u0645|\u0647\u0633\u062A|\u0627\u0633\u062A|\u0628\u0627|\u0628\u0631\u0634|\u0628\u0631|\u0645\u06CC\u062E\u0648\u0627\u0645|\u0645\u06CC\u200C\u062E\u0648\u0627\u0645)|[\u060C,.]|$)"
    );

    private static final Set<String> ALLOWED_AI_PARAMETERS = Set.of(
            "landAreaM2",
            "frontageM",
            "location",
            "coverageRatio",
            "allowedResidentialFloors",
            "commonAreaRatio",
            "landPricePerM2",
            "landValue",
            "constructionCostPerM2",
            "salePricePerM2",
            "ownerSharePercent",
            "permitCost",
            "demolitionCost",
            "insuranceCost",
            "engineeringCost",
            "financingCost",
            "otherCosts",
            "grossConstructionAreaM2",
            "saleableAreaM2",
            "currencyUnit"
    );

    private final PersianGlossaryService glossary;
    private final AiModelRegistry modelRegistry;
    private final PersianAgentPromptService promptService;

    public PersianAgent(
            PersianGlossaryService glossary,
            AiModelRegistry modelRegistry,
            PersianAgentPromptService promptService
    ) {
        this.glossary = glossary;
        this.modelRegistry = modelRegistry;
        this.promptService = promptService;
    }

    @Override
    public AgentType type() {
        return AgentType.PERSIAN;
    }

    @Override
    public String displayName() {
        return "\u0639\u0627\u0645\u0644 \u0632\u0628\u0627\u0646 \u0641\u0627\u0631\u0633\u06CC";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        String normalizedText = normalize(request.message());
        AgentIntent fallbackIntent = detectIntent(normalizedText);
        LinkedHashMap<String, Object> deterministicParameters = extractDeterministic(normalizedText);
        LinkedHashMap<String, Object> normalizedParameters = new LinkedHashMap<>();
        ArrayList<String> warnings = new ArrayList<>();

        AgentIntent intent = fallbackIntent;
        String parserMode = "RULE_FALLBACK";
        String providerId = null;
        String modelId = null;
        long inputTokens = 0L;
        long outputTokens = 0L;

        Optional<AiModelProvider> activeProvider = modelRegistry.activeProvider();
        if (activeProvider.isPresent()) {
            AiModelProvider provider = activeProvider.get();
            providerId = provider.providerId();
            modelId = provider.modelId();

            try {
                if (provider.available()) {
                    AiModelProvider.AiModelResponse response = provider.generate(
                            new AiModelProvider.AiModelRequest(
                                    promptService.systemPrompt(),
                                    normalizedText,
                                    Map.of(
                                            "existingParameters", request.parameters(),
                                            "recognizedGlossaryTerms", glossary.recognizedTerms(normalizedText)
                                    )
                            )
                    );

                    Map<String, Object> aiData = response.structuredData();
                    Map<String, Object> aiParameters = AgentValues.map(aiData.get("parameters"));
                    normalizedParameters.putAll(sanitizeAiParameters(aiParameters));

                    AgentIntent aiIntent = parseIntent(AgentValues.text(aiData, "intent"));
                    if (fallbackIntent == AgentIntent.PROPERTY_ANALYSIS && aiIntent != null) {
                        intent = aiIntent;
                    }

                    parserMode = "LOCAL_AI";
                    modelId = response.model();
                    inputTokens = response.inputTokens();
                    outputTokens = response.outputTokens();
                } else {
                    warnings.add(
                            "Ollama \u062F\u0631 \u062F\u0633\u062A\u0631\u0633 \u0646\u06CC\u0633\u062A \u06CC\u0627 \u0645\u062F\u0644 \u00AB" + provider.modelId()
                                    + "\u00BB \u0647\u0646\u0648\u0632 \u062F\u0627\u0646\u0644\u0648\u062F \u0646\u0634\u062F\u0647\u061B \u062A\u062D\u0644\u06CC\u0644 \u0642\u0648\u0627\u0639\u062F\u06CC \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0634\u062F."
                    );
                }
            } catch (RuntimeException ex) {
                warnings.add(
                        "\u0645\u062F\u0644 \u0645\u062D\u0644\u06CC \u067E\u0627\u0633\u062E \u0645\u0639\u062A\u0628\u0631 \u0646\u062F\u0627\u062F\u061B \u062A\u062D\u0644\u06CC\u0644 \u0642\u0648\u0627\u0639\u062F\u06CC \u062C\u0627\u06CC\u06AF\u0632\u06CC\u0646 \u0634\u062F. \u062C\u0632\u0626\u06CC\u0627\u062A: "
                                + safeMessage(ex)
                );
            }
        } else {
            warnings.add("Provider \u0647\u0648\u0634 \u0645\u0635\u0646\u0648\u0639\u06CC \u0641\u0639\u0627\u0644 \u0646\u06CC\u0633\u062A\u061B \u062A\u062D\u0644\u06CC\u0644 \u0642\u0648\u0627\u0639\u062F\u06CC \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0634\u062F.");
        }

        // Deterministic extraction wins for fields where the Java parser is certain.
        normalizedParameters.putAll(deterministicParameters);

        Map<String, String> recognizedTerms = glossary.recognizedTerms(normalizedText);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("intent", intent.name());
        data.put("normalizedText", normalizedText);
        data.put("normalizedParameters", normalizedParameters);
        data.put("recognizedTerms", recognizedTerms);
        data.put("parserMode", parserMode);
        if (providerId != null) {
            data.put("aiProvider", providerId);
            data.put("aiModel", modelId);
            data.put("aiInputTokens", inputTokens);
            data.put("aiOutputTokens", outputTokens);
        }

        double confidence = "LOCAL_AI".equals(parserMode)
                ? (normalizedParameters.isEmpty() ? 0.82d : 0.92d)
                : (normalizedParameters.isEmpty() ? 0.60d : 0.78d);

        String message = "LOCAL_AI".equals(parserMode)
                ? "\u0645\u062A\u0646 \u0641\u0627\u0631\u0633\u06CC \u0628\u0627 \u0645\u062F\u0644 \u0645\u062D\u0644\u06CC \u062A\u062D\u0644\u06CC\u0644 \u0648 \u0628\u0647 \u062F\u0627\u062F\u0647 \u0627\u0633\u062A\u0627\u0646\u062F\u0627\u0631\u062F \u062A\u0628\u062F\u06CC\u0644 \u0634\u062F."
                : "\u0645\u062A\u0646 \u0641\u0627\u0631\u0633\u06CC \u0628\u0627 \u062A\u062D\u0644\u06CC\u0644 \u0642\u0648\u0627\u0639\u062F\u06CC \u0646\u0631\u0645\u0627\u0644\u200C\u0633\u0627\u0632\u06CC \u0648 \u0647\u062F\u0641 \u062F\u0631\u062E\u0648\u0627\u0633\u062A \u062A\u0634\u062E\u06CC\u0635 \u062F\u0627\u062F\u0647 \u0634\u062F.";

        return new AgentResult(
                type(),
                AgentStatus.SUCCESS,
                message,
                data,
                warnings,
                List.of(),
                confidence,
                Instant.now()
        );
    }

    private LinkedHashMap<String, Object> extractDeterministic(String normalizedText) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        BigDecimal area = firstDecimal(AREA_PATTERN, normalizedText);
        BigDecimal frontage = firstDecimal(FRONTAGE_PATTERN, normalizedText);
        String location = firstText(LOCATION_PATTERN, normalizedText);

        if (area != null) {
            parameters.put("landAreaM2", area);
        }
        if (frontage != null) {
            parameters.put("frontageM", frontage);
        }
        if (location != null) {
            parameters.put("location", location);
        }
        return parameters;
    }

    private Map<String, Object> sanitizeAiParameters(Map<String, Object> input) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key == null || value == null || !ALLOWED_AI_PARAMETERS.contains(key)) {
                return;
            }
            if ("currencyUnit".equals(key)) {
                String unit = String.valueOf(value).trim().toUpperCase();
                if ("RIAL".equals(unit) || "TOMAN".equals(unit)) {
                    result.put(key, unit);
                }
                return;
            }
            if ("location".equals(key)) {
                String location = String.valueOf(value).trim();
                if (!location.isBlank()) {
                    result.put(key, location);
                }
                return;
            }
            BigDecimal decimal = AgentValues.decimal(value);
            if (decimal != null) {
                result.put(key, decimal);
            }
        });
        return result;
    }

    private AgentIntent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgentIntent.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private AgentIntent detectIntent(String text) {
        if (containsAny(text,
                "\u0628\u0631\u0631\u0633\u06CC \u0642\u0631\u0627\u0631\u062F\u0627\u062F", "\u0642\u0631\u0627\u0631\u062F\u0627\u062F \u0631\u0627", "\u0642\u0631\u0627\u0631\u062F\u0627\u062F \u0631\u0648", "\u0628\u0646\u062F \u0642\u0631\u0627\u0631\u062F\u0627\u062F")) {
            return AgentIntent.CONTRACT_REVIEW;
        }
        if (containsAny(text,
                "\u0633\u0627\u0632\u0646\u062F\u0647 \u0645\u0646\u0627\u0633\u0628", "\u067E\u06CC\u062F\u0627 \u06A9\u0631\u062F\u0646 \u0633\u0627\u0632\u0646\u062F\u0647", "\u067E\u06CC\u062F\u0627 \u06A9\u0646 \u0633\u0627\u0632\u0646\u062F\u0647", "\u062A\u0637\u0628\u06CC\u0642 \u0633\u0627\u0632\u0646\u062F\u0647")) {
            return AgentIntent.BUILDER_MATCHING;
        }
        if (containsAny(text,
                "\u062A\u062D\u0642\u06CC\u0642", "\u062C\u0633\u062A\u062C\u0648", "\u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0628\u0627\u0632\u0627\u0631", "\u0628\u0631\u0631\u0633\u06CC \u0645\u0646\u0627\u0628\u0639")) {
            return AgentIntent.RESEARCH;
        }
        if (containsAny(text,
                "\u0642\u06CC\u0645\u062A", "\u0627\u0631\u0632\u0634 \u0645\u0644\u06A9", "\u0627\u0631\u0632\u0634 \u0632\u0645\u06CC\u0646", "\u0645\u062A\u0631\u06CC \u0686\u0646\u062F")) {
            return AgentIntent.PROPERTY_VALUATION;
        }
        if (containsAny(text,
                "\u0686\u0646\u062F \u0637\u0628\u0642\u0647", "\u0686\u0642\u062F\u0631 \u0645\u06CC\u0634\u0647 \u0633\u0627\u062E\u062A", "\u0686\u0642\u062F\u0631 \u0645\u06CC\u200C\u0634\u0648\u062F \u0633\u0627\u062E\u062A", "\u0633\u0637\u062D \u0627\u0634\u063A\u0627\u0644", "\u062A\u0631\u0627\u06A9\u0645")) {
            return AgentIntent.BUILDABILITY_ANALYSIS;
        }
        if (containsAny(text,
                "\u0645\u0634\u0627\u0631\u06A9\u062A", "\u0635\u0631\u0641\u0647", "\u0633\u0648\u062F \u067E\u0631\u0648\u0698\u0647", "\u0633\u0647\u0645 \u0645\u0627\u0644\u06A9", "\u0633\u0647\u0645 \u0633\u0627\u0632\u0646\u062F\u0647")) {
            return AgentIntent.PARTNERSHIP_ANALYSIS;
        }
        return AgentIntent.PROPERTY_ANALYSIS;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal firstDecimal(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstText(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.isBlank() ? null : value;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            value.append(switch (c) {
                case '\u06F0', '\u0660' -> '0';
                case '\u06F1', '\u0661' -> '1';
                case '\u06F2', '\u0662' -> '2';
                case '\u06F3', '\u0663' -> '3';
                case '\u06F4', '\u0664' -> '4';
                case '\u06F5', '\u0665' -> '5';
                case '\u06F6', '\u0666' -> '6';
                case '\u06F7', '\u0667' -> '7';
                case '\u06F8', '\u0668' -> '8';
                case '\u06F9', '\u0669' -> '9';
                case '\u0643' -> '\u06A9';
                case '\u064A' -> '\u06CC';
                default -> c;
            });
        }
        return value.toString().replaceAll("\\s+", " ").trim();
    }

    private String safeMessage(RuntimeException ex) {
        String value = ex.getMessage();
        return value == null || value.isBlank()
                ? ex.getClass().getSimpleName()
                : value;
    }
}
