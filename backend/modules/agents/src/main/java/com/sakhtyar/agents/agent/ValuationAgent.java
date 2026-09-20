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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ValuationAgent implements SakhtyarAgent {

    @Override
    public AgentType type() {
        return AgentType.VALUATION;
    }

    @Override
    public String displayName() {
        return "عامل قیمت‌گذاری";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        AgentResult property = context.resultOf(AgentType.PROPERTY)
                .orElseGet(() -> context.call(AgentType.PROPERTY, request));

        BigDecimal landArea = first(
                AgentValues.decimal(request.parameters(), "landAreaM2"),
                AgentValues.decimal(property.data(), "landAreaM2")
        );
        BigDecimal pricePerM2 = AgentValues.decimal(
                request.parameters(),
                "landPricePerM2",
                "pricePerM2"
        );
        String source = "USER_INPUT";
        double confidence = 0.95d;

        List<Map<String, Object>> comparables = AgentValues.mapList(
                request.parameters().get("comparables")
        );
        if (pricePerM2 == null) {
            List<BigDecimal> prices = new ArrayList<>();
            for (Map<String, Object> comparable : comparables) {
                BigDecimal price = AgentValues.decimal(
                        comparable,
                        "landPricePerM2",
                        "pricePerM2"
                );
                if (price != null && price.signum() > 0) {
                    prices.add(price);
                }
            }
            if (!prices.isEmpty()) {
                pricePerM2 = median(prices);
                source = "COMPARABLE_MEDIAN";
                confidence = Math.min(0.85d, 0.45d + (prices.size() * 0.08d));
            }
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        if (pricePerM2 != null) {
            data.put("landPricePerM2", AgentNumbers.money(pricePerM2));
            data.put("valuationSource", source);
            data.put("comparableCount", comparables.size());
        }
        if (landArea != null) {
            data.put("landAreaM2", landArea);
        }
        if (pricePerM2 != null && landArea != null) {
            data.put(
                    "estimatedLandValue",
                    AgentNumbers.money(pricePerM2.multiply(landArea))
            );
        }

        if (pricePerM2 == null) {
            return new AgentResult(
                    type(),
                    AgentStatus.NEEDS_INPUT,
                    "برای قیمت‌گذاری باید قیمت هر متر زمین یا املاک مشابه معتبر ارائه شود.",
                    data,
                    List.of("Agent قیمت بازار را بدون منبع داده حدس نمی‌زند."),
                    List.of("landPricePerM2 یا comparables"),
                    0.0d,
                    Instant.now()
            );
        }

        AgentStatus status = landArea == null ? AgentStatus.PARTIAL : AgentStatus.SUCCESS;
        return new AgentResult(
                type(),
                status,
                "قیمت‌گذاری بر اساس داده ورودی و محاسبه آماری انجام شد.",
                data,
                List.of(),
                landArea == null ? List.of("landAreaM2") : List.of(),
                confidence,
                Instant.now()
        );
    }

    private BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1)
                .add(sorted.get(middle))
                .divide(BigDecimal.valueOf(2));
    }

    private BigDecimal first(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }
}
