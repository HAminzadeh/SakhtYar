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
        return AssemblyCalculationPolicy.effectiveQuantity(componentQuantity, wasteFactor, requestedAssemblyQuantity);
    }

    public static BigDecimal choosePrice(
            PriceBasis basis,
            BigDecimal min,
            BigDecimal max,
            BigDecimal average,
            BigDecimal median
    ) {
        if(basis==null)throw new IllegalArgumentException("Price basis required.");
        BigDecimal selected = switch (basis) {
            case MIN -> min;
            case MAX -> max;
            case AVERAGE -> average;
            case MEDIAN -> median;
        };
        AssemblyCalculationPolicy.validateStatistics(min, max, average, median);
        return selected;
    }

    public static BigDecimal lineTotal(BigDecimal effectiveQuantity, BigDecimal unitPrice) {
        return AssemblyCalculationPolicy.lineTotal(effectiveQuantity, unitPrice);
    }
}