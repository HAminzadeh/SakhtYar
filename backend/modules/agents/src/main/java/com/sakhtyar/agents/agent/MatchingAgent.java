package com.sakhtyar.agents.agent;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.SakhtyarAgent;
import com.sakhtyar.agents.support.AgentValues;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MatchingAgent implements SakhtyarAgent {

    @Override
    public AgentType type() {
        return AgentType.MATCHING;
    }

    @Override
    public String displayName() {
        return "عامل تطبیق مالک و سازنده";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        List<Map<String, Object>> builders = AgentValues.mapList(
                request.parameters().get("builders")
        );
        if (builders.isEmpty()) {
            return AgentResult.needsInput(
                    type(),
                    "برای تطبیق، فهرست سازندگان یا اتصال به دایرکتوری سازندگان لازم است.",
                    Map.of(),
                    List.of("builders")
            );
        }

        AgentResult property = context.resultOf(AgentType.PROPERTY)
                .orElseGet(() -> context.call(AgentType.PROPERTY, request));
        String district = firstText(
                AgentValues.text(request.parameters(), "district"),
                AgentValues.text(property.data(), "district")
        );
        BigDecimal projectArea = first(
                AgentValues.decimal(request.parameters(), "projectAreaM2"),
                AgentValues.decimal(property.data(), "landAreaM2")
        );
        BigDecimal requiredCapital = AgentValues.decimal(
                request.parameters(),
                "requiredCapital"
        );

        ArrayList<Map<String, Object>> ranked = new ArrayList<>();
        for (Map<String, Object> builder : builders) {
            ranked.add(score(builder, district, projectArea, requiredCapital));
        }
        ranked.sort(Comparator.comparingInt(
                item -> -((Number) item.get("score")).intValue()
        ));

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("matches", ranked.stream().limit(10).toList());
        data.put("candidateCount", builders.size());
        AgentValues.putIfPresent(data, "targetDistrict", district);
        AgentValues.putIfPresent(data, "projectAreaM2", projectArea);
        AgentValues.putIfPresent(data, "requiredCapital", requiredCapital);

        return new AgentResult(
                type(),
                AgentStatus.SUCCESS,
                "سازندگان بر اساس معیارهای شفاف و قطعی تطبیق داده شدند.",
                data,
                List.of("امتیاز تطبیق یک ابزار غربال‌گری است و جایگزین اعتبارسنجی مالی و فنی نیست."),
                List.of(),
                0.85d,
                Instant.now()
        );
    }

    private Map<String, Object> score(
            Map<String, Object> builder,
            String district,
            BigDecimal projectArea,
            BigDecimal requiredCapital
    ) {
        int score = 0;
        ArrayList<String> reasons = new ArrayList<>();

        if (AgentValues.bool(builder, "available", true)) {
            score += 25;
            reasons.add("ظرفیت پذیرش پروژه");
        }

        List<String> districts = AgentValues.stringList(builder.get("districts"));
        if (district != null && districts.stream().anyMatch(district::equalsIgnoreCase)) {
            score += 25;
            reasons.add("سابقه/تمرکز در منطقه هدف");
        }

        BigDecimal maxArea = AgentValues.decimal(builder, "maxProjectAreaM2");
        if (projectArea != null && maxArea != null && maxArea.compareTo(projectArea) >= 0) {
            score += 25;
            reasons.add("ظرفیت متراژی مناسب");
        }

        BigDecimal capital = AgentValues.decimal(builder, "capitalCapacity");
        if (requiredCapital != null && capital != null && capital.compareTo(requiredCapital) >= 0) {
            score += 15;
            reasons.add("ظرفیت سرمایه کافی");
        }

        Integer experienceYears = AgentValues.integer(builder, "experienceYears");
        if (experienceYears != null && experienceYears > 0) {
            score += Math.min(10, experienceYears * 2);
            reasons.add("سابقه اجرایی");
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>(builder);
        result.put("score", Math.min(100, score));
        result.put("reasons", reasons);
        return result;
    }

    private BigDecimal first(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private String firstText(String first, String second) {
        return first != null ? first : second;
    }
}
