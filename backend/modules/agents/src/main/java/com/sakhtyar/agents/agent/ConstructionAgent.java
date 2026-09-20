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
import org.springframework.stereotype.Component;

@Component
public class ConstructionAgent implements SakhtyarAgent {

    @Override
    public AgentType type() {
        return AgentType.CONSTRUCTION;
    }

    @Override
    public String displayName() {
        return "عامل محاسبات ساخت";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        AgentResult property = context.resultOf(AgentType.PROPERTY)
                .orElseGet(() -> context.call(AgentType.PROPERTY, request));
        AgentResult municipality = context.resultOf(AgentType.MUNICIPALITY)
                .orElseGet(() -> context.call(AgentType.MUNICIPALITY, request));

        BigDecimal landArea = first(
                AgentValues.decimal(request.parameters(), "landAreaM2"),
                AgentValues.decimal(property.data(), "landAreaM2")
        );
        BigDecimal coverage = first(
                AgentNumbers.normalizeRatio(AgentValues.decimal(
                        request.parameters(),
                        "coverageRatio"
                )),
                AgentValues.decimal(municipality.data(), "coverageRatio")
        );
        Integer floors = AgentValues.integer(
                request.parameters(),
                "allowedResidentialFloors",
                "allowedFloors"
        );
        if (floors == null) {
            floors = AgentValues.integer(
                    municipality.data(),
                    "allowedResidentialFloors"
            );
        }
        BigDecimal commonAreaRatio = first(
                AgentNumbers.normalizeRatio(AgentValues.decimal(
                        request.parameters(),
                        "commonAreaRatio"
                )),
                AgentValues.decimal(municipality.data(), "commonAreaRatio")
        );

        ArrayList<String> missing = new ArrayList<>();
        if (landArea == null || landArea.signum() <= 0) {
            missing.add("landAreaM2 / مساحت زمین");
        }
        if (coverage == null || coverage.signum() <= 0) {
            missing.add("coverageRatio / سطح اشغال");
        }
        if (floors == null || floors <= 0) {
            missing.add("allowedResidentialFloors / تعداد طبقات مجاز");
        }

        if (!missing.isEmpty()) {
            return AgentResult.needsInput(
                    type(),
                    "برای محاسبه زیربنا اطلاعات پایه ساخت کامل نیست.",
                    new LinkedHashMap<>(),
                    missing
            );
        }

        BigDecimal footprint = landArea.multiply(coverage);
        BigDecimal gross = footprint.multiply(BigDecimal.valueOf(floors));
        BigDecimal saleable = null;
        if (commonAreaRatio != null) {
            saleable = gross.multiply(BigDecimal.ONE.subtract(commonAreaRatio));
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("landAreaM2", AgentNumbers.area(landArea));
        data.put("coverageRatio", coverage);
        data.put("allowedResidentialFloors", floors);
        data.put("buildableFootprintM2", AgentNumbers.area(footprint));
        data.put("grossConstructionAreaM2", AgentNumbers.area(gross));
        if (commonAreaRatio != null) {
            data.put("commonAreaRatio", commonAreaRatio);
            data.put("estimatedSaleableAreaM2", AgentNumbers.area(saleable));
        }

        List<String> warnings = commonAreaRatio == null
                ? List.of("نسبت مشاعات وارد نشده؛ مساحت قابل فروش محاسبه نشد.")
                : List.of();
        AgentStatus status = commonAreaRatio == null
                ? AgentStatus.PARTIAL
                : AgentStatus.SUCCESS;

        return new AgentResult(
                type(),
                status,
                "محاسبات زیربنای ساخت با فرمول‌های قطعی Java انجام شد.",
                data,
                warnings,
                List.of(),
                0.95d,
                Instant.now()
        );
    }

    private BigDecimal first(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }
}
