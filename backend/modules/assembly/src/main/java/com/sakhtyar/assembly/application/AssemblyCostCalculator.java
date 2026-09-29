package com.sakhtyar.assembly.application;

import com.sakhtyar.assembly.domain.PriceBasis;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AssemblyCostCalculator {

    private AssemblyCostCalculator() {}

    public static BigDecimal effectiveQuantity(
            BigDecimal componentQuantity,
            BigDecimal wasteFactor,
            BigDecimal requestedAssemblyQuantity
    ) {
        return componentQuantity
                .multiply(BigDecimal.ONE.add(wasteFactor))
                .multiply(requestedAssemblyQuantity)
                .setScale(6, RoundingMode.HALF_UP);
    }

    public static BigDecimal choosePrice(
            PriceBasis basis,
            BigDecimal min,
            BigDecimal max,
            BigDecimal average,
            BigDecimal median
    ) {
        return switch (basis) {
            case MIN -> min;
            case MAX -> max;
            case AVERAGE -> average;
            case MEDIAN -> median;
        };
    }

    public static BigDecimal lineTotal(BigDecimal effectiveQuantity, BigDecimal unitPrice) {
        return effectiveQuantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}