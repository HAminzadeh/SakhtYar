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
public class FinancialAgent implements SakhtyarAgent {

    @Override
    public AgentType type() {
        return AgentType.FINANCIAL;
    }

    @Override
    public String displayName() {
        return "عامل تحلیل مالی";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        AgentResult valuation = context.resultOf(AgentType.VALUATION)
                .orElseGet(() -> context.call(AgentType.VALUATION, request));
        AgentResult construction = context.resultOf(AgentType.CONSTRUCTION)
                .orElseGet(() -> context.call(AgentType.CONSTRUCTION, request));

        BigDecimal landValue = first(
                AgentValues.decimal(request.parameters(), "landValue"),
                AgentValues.decimal(valuation.data(), "estimatedLandValue")
        );
        BigDecimal grossArea = first(
                AgentValues.decimal(request.parameters(), "grossConstructionAreaM2"),
                AgentValues.decimal(construction.data(), "grossConstructionAreaM2")
        );
        BigDecimal saleableArea = first(
                AgentValues.decimal(request.parameters(), "saleableAreaM2", "estimatedSaleableAreaM2"),
                AgentValues.decimal(construction.data(), "estimatedSaleableAreaM2")
        );
        BigDecimal constructionCostPerM2 = AgentValues.decimal(
                request.parameters(),
                "constructionCostPerM2"
        );
        BigDecimal salePricePerM2 = AgentValues.decimal(
                request.parameters(),
                "salePricePerM2"
        );

        ArrayList<String> missing = new ArrayList<>();
        if (landValue == null) missing.add("landValue / ارزش زمین");
        if (grossArea == null) missing.add("grossConstructionAreaM2 / زیربنای ناخالص");
        if (saleableArea == null) missing.add("saleableAreaM2 / متراژ قابل فروش");
        if (constructionCostPerM2 == null) missing.add("constructionCostPerM2 / هزینه ساخت هر متر");
        if (salePricePerM2 == null) missing.add("salePricePerM2 / قیمت فروش هر متر");

        if (!missing.isEmpty()) {
            LinkedHashMap<String, Object> partial = new LinkedHashMap<>();
            AgentValues.putIfPresent(partial, "landValue", landValue);
            AgentValues.putIfPresent(partial, "grossConstructionAreaM2", grossArea);
            AgentValues.putIfPresent(partial, "saleableAreaM2", saleableArea);
            return AgentResult.needsInput(
                    type(),
                    "برای تحلیل اقتصادی مشارکت، داده‌های مالی پایه کامل نیست.",
                    partial,
                    missing
            );
        }

        String[] optionalKeys = {
                "permitCost",
                "demolitionCost",
                "insuranceCost",
                "engineeringCost",
                "financingCost",
                "otherCosts"
        };
        BigDecimal extras = BigDecimal.ZERO;
        ArrayList<String> assumedZero = new ArrayList<>();
        for (String key : optionalKeys) {
            BigDecimal value = AgentValues.decimal(request.parameters(), key);
            if (value == null) {
                assumedZero.add(key);
            } else {
                extras = extras.add(value);
            }
        }

        BigDecimal constructionCost = grossArea.multiply(constructionCostPerM2);
        BigDecimal developerCashRequirement = constructionCost.add(extras);
        BigDecimal finalProjectValue = saleableArea.multiply(salePricePerM2);
        BigDecimal totalEconomicCost = developerCashRequirement.add(landValue);
        BigDecimal economicProfit = finalProjectValue.subtract(totalEconomicCost);
        BigDecimal projectRoi = AgentNumbers.ratio(economicProfit, totalEconomicCost);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("landValue", AgentNumbers.money(landValue));
        data.put("constructionCost", AgentNumbers.money(constructionCost));
        data.put("additionalCosts", AgentNumbers.money(extras));
        data.put("developerCashRequirement", AgentNumbers.money(developerCashRequirement));
        data.put("finalProjectValue", AgentNumbers.money(finalProjectValue));
        data.put("totalEconomicCost", AgentNumbers.money(totalEconomicCost));
        data.put("economicProfit", AgentNumbers.money(economicProfit));
        data.put("projectRoi", projectRoi);

        BigDecimal ownerShare = AgentNumbers.normalizeRatio(
                AgentValues.decimal(request.parameters(), "ownerSharePercent", "ownerShare")
        );
        if (ownerShare != null) {
            BigDecimal ownerValue = finalProjectValue.multiply(ownerShare);
            BigDecimal builderValue = finalProjectValue.subtract(ownerValue);
            data.put("ownerShareRatio", ownerShare);
            data.put("ownerShareValue", AgentNumbers.money(ownerValue));
            data.put("builderShareValue", AgentNumbers.money(builderValue));
            data.put(
                    "builderProfitAfterCashCost",
                    AgentNumbers.money(builderValue.subtract(developerCashRequirement))
            );
        }

        List<String> warnings = assumedZero.isEmpty()
                ? List.of()
                : List.of(
                        "این هزینه‌ها وارد نشده و در این سناریو صفر در نظر گرفته شدند: "
                                + String.join("، ", assumedZero)
                );

        return new AgentResult(
                type(),
                warnings.isEmpty() ? AgentStatus.SUCCESS : AgentStatus.PARTIAL,
                "تحلیل مالی با محاسبات قطعی Java انجام شد.",
                data,
                warnings,
                List.of(),
                warnings.isEmpty() ? 0.95d : 0.80d,
                Instant.now()
        );
    }

    private BigDecimal first(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }
}
