package com.sakhtyar.agents.agent;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentIntent;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.SakhtyarAgent;
import com.sakhtyar.agents.glossary.PersianGlossaryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            "(?:تو|در)\\s+([\\u0600-\\u06FF\\u200C\\s]+?)(?=\\s+(?:دارم|داریم|هست|است|با|برش|بر|میخوام|می‌خوام)|[،,.]|$)"
    );

    private final PersianGlossaryService glossary;

    public PersianAgent(PersianGlossaryService glossary) {
        this.glossary = glossary;
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
        AgentIntent intent = detectIntent(normalizedText);
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

        Map<String, String> recognizedTerms = glossary.recognizedTerms(normalizedText);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("intent", intent.name());
        data.put("normalizedText", normalizedText);
        data.put("normalizedParameters", parameters);
        data.put("recognizedTerms", recognizedTerms);

        return new AgentResult(
                type(),
                AgentStatus.SUCCESS,
                "متن فارسی نرمال‌سازی و هدف درخواست تشخیص داده شد.",
                data,
                List.of(),
                List.of(),
                parameters.isEmpty() ? 0.65d : 0.80d,
                Instant.now()
        );
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
                case '۰', '٠' -> '0';
                case '۱', '١' -> '1';
                case '۲', '٢' -> '2';
                case '۳', '٣' -> '3';
                case '۴', '٤' -> '4';
                case '۵', '٥' -> '5';
                case '۶', '٦' -> '6';
                case '۷', '٧' -> '7';
                case '۸', '٨' -> '8';
                case '۹', '٩' -> '9';
                case 'ك' -> 'ک';
                case 'ي' -> 'ی';
                default -> c;
            });
        }
        return value.toString().replaceAll("\\s+", " ").trim();
    }
}
