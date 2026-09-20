package com.sakhtyar.agents.agent;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.SakhtyarAgent;
import com.sakhtyar.agents.support.AgentNumbers;
import com.sakhtyar.agents.support.AgentValues;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MunicipalityAgent implements SakhtyarAgent {

    @Override
    public AgentType type() {
        return AgentType.MUNICIPALITY;
    }

    @Override
    public String displayName() {
        return "عامل ضوابط شهرداری";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        Map<String, Object> rules = AgentValues.map(
                request.parameters().get("municipalityRules")
        );
        Map<String, Object> source = rules.isEmpty()
                ? request.parameters()
                : rules;

        BigDecimal coverageRatio = AgentNumbers.normalizeRatio(
                AgentValues.decimal(source, "coverageRatio", "occupancyRatio")
        );
        Integer floors = AgentValues.integer(
                source,
                "allowedResidentialFloors",
                "allowedFloors"
        );
        BigDecimal commonAreaRatio = AgentNumbers.normalizeRatio(
                AgentValues.decimal(source, "commonAreaRatio")
        );
        BigDecimal setbackM = AgentValues.decimal(source, "setbackM");
        BigDecimal parkingRequiredPerUnit = AgentValues.decimal(
                source,
                "parkingRequiredPerUnit"
        );

        AgentResult property = context.resultOf(AgentType.PROPERTY)
                .orElseGet(() -> context.call(AgentType.PROPERTY, request));

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        AgentValues.putIfPresent(data, "coverageRatio", coverageRatio);
        AgentValues.putIfPresent(data, "allowedResidentialFloors", floors);
        AgentValues.putIfPresent(data, "commonAreaRatio", commonAreaRatio);
        AgentValues.putIfPresent(data, "setbackM", setbackM);
        AgentValues.putIfPresent(data, "parkingRequiredPerUnit", parkingRequiredPerUnit);
        AgentValues.putIfPresent(data, "city", property.data().get("city"));
        AgentValues.putIfPresent(data, "district", property.data().get("district"));
        data.put("source", rules.isEmpty() ? "REQUEST_PARAMETERS" : "MUNICIPALITY_RULES_INPUT");

        ArrayList<String> missing = new ArrayList<>();
        if (coverageRatio == null) {
            missing.add("coverageRatio / سطح اشغال مجاز");
        }
        if (floors == null || floors <= 0) {
            missing.add("allowedResidentialFloors / تعداد طبقات مجاز");
        }

        if (!missing.isEmpty()) {
            return new AgentResult(
                    type(),
                    AgentStatus.NEEDS_INPUT,
                    "اتصال رسمی ضوابط شهرداری هنوز به این ماژول وصل نشده است؛ ضوابط قطعی باید از سرویس معتبر یا ورودی کارشناس تأمین شود.",
                    data,
                    List.of("Agent عمداً ضابطه شهرسازی را حدس نمی‌زند."),
                    missing,
                    0.0d,
                    Instant.now()
            );
        }

        return new AgentResult(
                type(),
                AgentStatus.SUCCESS,
                "ضوابط شهرسازی ورودی استانداردسازی شد.",
                data,
                List.of(),
                List.of(),
                rules.isEmpty() ? 0.70d : 0.90d,
                Instant.now()
        );
    }
}
