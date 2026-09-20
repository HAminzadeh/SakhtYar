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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PersianAgent implements SakhtyarAgent {

    private static final Pattern AREA_PATTERN = Pattern.compile(
            "(?:(?:زمین|ملک)\\s*)?(\\d+(?:\\.\\d+)?)\\s*(?:متر\\s*مربع|متری)"
    );

    private static final Pattern FRONTAGE_PATTERN = Pattern.compile(
            "بر(?:ش|\\s*ملک)?\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)"
    );

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?:تو|در)\\s+([\\u0600-\\u06FF\\u200C\\s]+?)(?=\\s+(?:دارم|داریم|هست|است|با|برش|بر|میخوام|می‌خوام|که)|[،,.]|$)"
    );

    private static final Pattern CONSTRUCTION_YEAR_PATTERN = Pattern.compile(
            "(?:(?:سال\\s*ساخت|ساخت\\s*سال|مال\\s*سال|برای\\s*سال)\\s*[:=]?\\s*)(\\d{4})"
    );

    private static final Pattern EXISTING_FLOORS_DIGIT_PATTERN = Pattern.compile(
            "(\\d+)\\s*طبقه"
    );

    private static final Pattern EXISTING_FLOORS_WORD_PATTERN = Pattern.compile(
            "(یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده)\\s*طبقه"
    );

    private static final Pattern EXISTING_UNITS_DIGIT_PATTERN = Pattern.compile(
            "(\\d+)\\s*واحد(?:ی)?"
    );

    private static final Pattern EXISTING_UNITS_WORD_PATTERN = Pattern.compile(
            "(یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده)\\s*واحد(?:ی)?"
    );

    private static final Set<String> AI_NUMERIC_PARAMETERS = Set.of(
            "landAreaM2",
            "frontageM",
            "passageWidthM",
            "buildingAreaM2",
            "constructionYear",
            "existingFloors",
            "existingUnits",
            "parkingSpaces",
            "basementFloors",
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
            "saleableAreaM2"
    );

    private static final Set<String> AI_STRING_PARAMETERS = Set.of(
            "location",
            "province",
            "city",
            "district",
            "neighborhood",
            "address",
            "orientation",
            "propertyType",
            "buildingCondition",
            "cornerPosition",
            "zoneCode",
            "landUse",
            "currencyUnit"
    );

    private static final Set<String> AI_BOOLEAN_PARAMETERS = Set.of(
            "hasElevator",
            "hasParking",
            "hasBasement",
            "isCorner",
            "isVacantLand"
    );

    private static final Set<String> ORIENTATIONS = Set.of(
            "NORTH",
            "SOUTH",
            "EAST",
            "WEST",
            "NORTH_EAST",
            "NORTH_WEST",
            "SOUTH_EAST",
            "SOUTH_WEST"
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
        return "عامل زبان فارسی";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        String normalizedText = normalize(request.message());
        AgentIntent fallbackIntent = detectIntent(normalizedText);
        LinkedHashMap<String, Object> deterministicParameters =
                extractDeterministic(normalizedText);
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
                                            "recognizedGlossaryTerms",
                                            glossary.recognizedTerms(normalizedText)
                                    )
                            )
                    );

                    Map<String, Object> aiData = response.structuredData();
                    Map<String, Object> aiParameters =
                            AgentValues.map(aiData.get("parameters"));
                    normalizedParameters.putAll(sanitizeAiParameters(aiParameters));

                    AgentIntent aiIntent =
                            parseIntent(AgentValues.text(aiData, "intent"));
                    if (fallbackIntent == AgentIntent.PROPERTY_ANALYSIS
                            && aiIntent != null) {
                        intent = aiIntent;
                    }

                    parserMode = "LOCAL_AI";
                    modelId = response.model();
                    inputTokens = response.inputTokens();
                    outputTokens = response.outputTokens();
                } else {
                    warnings.add(
                            "Ollama در دسترس نیست یا مدل «"
                                    + provider.modelId()
                                    + "» هنوز دانلود نشده؛ تحلیل قواعدی استفاده شد."
                    );
                }
            } catch (RuntimeException ex) {
                warnings.add(
                        "مدل محلی پاسخ معتبر نداد؛ تحلیل قواعدی جایگزین شد. جزئیات: "
                                + safeMessage(ex)
                );
            }
        } else {
            warnings.add(
                    "Provider هوش مصنوعی فعال نیست؛ تحلیل قواعدی استفاده شد."
            );
        }

        // Deterministic extraction wins where Java can parse the statement safely.
        normalizedParameters.putAll(deterministicParameters);

        Map<String, String> recognizedTerms =
                glossary.recognizedTerms(normalizedText);
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
                : (normalizedParameters.isEmpty() ? 0.60d : 0.82d);

        String message = "LOCAL_AI".equals(parserMode)
                ? "متن فارسی با مدل محلی تحلیل و به داده استاندارد تبدیل شد."
                : "متن فارسی با تحلیل قواعدی نرمال‌سازی و هدف درخواست تشخیص داده شد.";

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

    private LinkedHashMap<String, Object> extractDeterministic(
            String normalizedText
    ) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();

        putIfNotNull(
                parameters,
                "landAreaM2",
                firstDecimal(AREA_PATTERN, normalizedText)
        );
        putIfNotNull(
                parameters,
                "frontageM",
                firstDecimal(FRONTAGE_PATTERN, normalizedText)
        );
        putIfNotNull(
                parameters,
                "location",
                firstText(LOCATION_PATTERN, normalizedText)
        );
        putIfNotNull(
                parameters,
                "constructionYear",
                firstDecimal(CONSTRUCTION_YEAR_PATTERN, normalizedText)
        );

        Integer existingFloors = extractExistingCount(
                normalizedText,
                EXISTING_FLOORS_DIGIT_PATTERN,
                EXISTING_FLOORS_WORD_PATTERN,
                true
        );
        if (existingFloors != null) {
            parameters.put("existingFloors", existingFloors);
        }

        Integer existingUnits = extractExistingCount(
                normalizedText,
                EXISTING_UNITS_DIGIT_PATTERN,
                EXISTING_UNITS_WORD_PATTERN,
                false
        );
        if (existingUnits != null) {
            parameters.put("existingUnits", existingUnits);
        }

        String orientation = extractOrientation(normalizedText);
        if (orientation != null) {
            parameters.put("orientation", orientation);
        }

        return parameters;
    }

    private Map<String, Object> sanitizeAiParameters(Map<String, Object> input) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();

        input.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }

            if (AI_NUMERIC_PARAMETERS.contains(key)) {
                BigDecimal decimal = AgentValues.decimal(value);
                if (decimal != null) {
                    result.put(key, decimal);
                }
                return;
            }

            if (AI_STRING_PARAMETERS.contains(key)) {
                String text = String.valueOf(value).trim();
                if (text.isBlank()) {
                    return;
                }

                if ("currencyUnit".equals(key)) {
                    String unit = text.toUpperCase(Locale.ROOT);
                    if ("RIAL".equals(unit) || "TOMAN".equals(unit)) {
                        result.put(key, unit);
                    }
                    return;
                }

                if ("orientation".equals(key)) {
                    String orientation = normalizeOrientation(text);
                    if (orientation != null) {
                        result.put(key, orientation);
                    }
                    return;
                }

                result.put(key, text);
                return;
            }

            if (AI_BOOLEAN_PARAMETERS.contains(key)) {
                Boolean bool = booleanValue(value);
                if (bool != null) {
                    result.put(key, bool);
                }
            }
        });

        return result;
    }

    private Integer extractExistingCount(
            String text,
            Pattern digitPattern,
            Pattern wordPattern,
            boolean floor
    ) {
        Matcher digit = digitPattern.matcher(text);
        while (digit.find()) {
            if (floor && isAllowedFloorContext(text, digit.start(), digit.end())) {
                continue;
            }
            try {
                return Integer.parseInt(digit.group(1));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }

        Matcher word = wordPattern.matcher(text);
        while (word.find()) {
            if (floor && isAllowedFloorContext(text, word.start(), word.end())) {
                continue;
            }
            Integer value = persianNumberWord(word.group(1));
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private boolean isAllowedFloorContext(String text, int start, int end) {
        int from = Math.max(0, start - 18);
        int to = Math.min(text.length(), end + 18);
        String around = text.substring(from, to);
        return containsAny(
                around,
                "مجاز",
                "قابل ساخت",
                "اجازه ساخت",
                "تراکم"
        );
    }

    private String extractOrientation(String text) {
        boolean north = text.contains("شمالی");
        boolean south = text.contains("جنوبی");
        boolean east = text.contains("شرقی");
        boolean west = text.contains("غربی");

        if (north && east) return "NORTH_EAST";
        if (north && west) return "NORTH_WEST";
        if (south && east) return "SOUTH_EAST";
        if (south && west) return "SOUTH_WEST";
        if (north) return "NORTH";
        if (south) return "SOUTH";
        if (east) return "EAST";
        if (west) return "WEST";
        return null;
    }

    private String normalizeOrientation(String value) {
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        if (ORIENTATIONS.contains(normalized)) {
            return normalized;
        }

        return switch (value.trim()) {
            case "شمالی" -> "NORTH";
            case "جنوبی" -> "SOUTH";
            case "شرقی" -> "EAST";
            case "غربی" -> "WEST";
            case "شمال شرقی", "شمال‌شرقی" -> "NORTH_EAST";
            case "شمال غربی", "شمال‌غربی" -> "NORTH_WEST";
            case "جنوب شرقی", "جنوب‌شرقی" -> "SOUTH_EAST";
            case "جنوب غربی", "جنوب‌غربی" -> "SOUTH_WEST";
            default -> null;
        };
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }

        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "yes", "1", "بله", "دارد", "هست" -> true;
            case "false", "no", "0", "خیر", "ندارد", "نیست" -> false;
            default -> null;
        };
    }

    private Integer persianNumberWord(String value) {
        return switch (value) {
            case "یک" -> 1;
            case "دو" -> 2;
            case "سه" -> 3;
            case "چهار" -> 4;
            case "پنج" -> 5;
            case "شش" -> 6;
            case "هفت" -> 7;
            case "هشت" -> 8;
            case "نه" -> 9;
            case "ده" -> 10;
            default -> null;
        };
    }

    private void putIfNotNull(
            Map<String, Object> target,
            String key,
            Object value
    ) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private AgentIntent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgentIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private AgentIntent detectIntent(String text) {
        if (containsAny(text,
                "بررسی قرارداد", "قرارداد را", "قرارداد رو", "بند قرارداد")) {
            return AgentIntent.CONTRACT_REVIEW;
        }
        if (containsAny(text,
                "سازنده مناسب", "پیدا کردن سازنده", "پیدا کن سازنده", "تطبیق سازنده")) {
            return AgentIntent.BUILDER_MATCHING;
        }
        if (containsAny(text,
                "تحقیق", "جستجو", "اطلاعات بازار", "بررسی منابع")) {
            return AgentIntent.RESEARCH;
        }
        if (containsAny(text,
                "قیمت", "ارزش ملک", "ارزش زمین", "متری چند")) {
            return AgentIntent.PROPERTY_VALUATION;
        }
        if (containsAny(text,
                "چند طبقه", "چقدر میشه ساخت", "چقدر می‌شود ساخت", "سطح اشغال", "تراکم")) {
            return AgentIntent.BUILDABILITY_ANALYSIS;
        }
        if (containsAny(text,
                "مشارکت", "صرفه", "سود پروژه", "سهم مالک", "سهم سازنده")) {
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

        return value.toString()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeMessage(RuntimeException ex) {
        String value = ex.getMessage();
        return value == null || value.isBlank()
                ? ex.getClass().getSimpleName()
                : value;
    }
}
