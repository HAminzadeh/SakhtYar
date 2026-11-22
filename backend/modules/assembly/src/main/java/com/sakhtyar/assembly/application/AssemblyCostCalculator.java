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
        nonNegative(componentQuantity,"componentQuantity");
        nonNegative(wasteFactor,"wasteFactor");
        nonNegative(requestedAssemblyQuantity,"requestedAssemblyQuantity");
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
        if(basis==null)throw new IllegalArgumentException("Price basis required.");
        BigDecimal selected = switch (basis) {
            case MIN -> min;
            case MAX -> max;
            case AVERAGE -> average;
            case MEDIAN -> median;
        };
        nonNegative(selected,"chosen unit price");
        return selected;
    }

    private static void nonNegative(BigDecimal value,String field){
        if(value==null || value.signum()<0)throw new IllegalArgumentException(field+" must be non-negative.");
    }

    public static BigDecimal lineTotal(BigDecimal effectiveQuantity, BigDecimal unitPrice) {
        nonNegative(effectiveQuantity,"effectiveQuantity");
        nonNegative(unitPrice,"unitPrice");
        return effectiveQuantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}