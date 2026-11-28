package com.sakhtyar.assembly.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Version 2.0: bounded decimal arithmetic, no floating point, no invented prices. */
public final class AssemblyCalculationPolicy {
    private AssemblyCalculationPolicy() {}
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    private static BigDecimal decimal(BigDecimal value, int integerDigits, int scale, boolean positive, String field) {
        if (value == null || (positive ? value.signum() <= 0 : value.signum() < 0))
            throw new IllegalArgumentException(field + " must be " + (positive ? "positive." : "non-negative."));
        // Reject hostile exponents and precision before multiplication or setScale.
        if (value.precision() > 100 || Math.abs((long)value.scale()) > 100)
            throw new IllegalArgumentException(field + " exceeds decimal input bounds.");
        BigDecimal d = value.stripTrailingZeros();
        if ((long)d.precision() - d.scale() > integerDigits || d.scale() > scale)
            throw new IllegalArgumentException(field + " exceeds storage precision.");
        return d.setScale(scale, RoundingMode.UNNECESSARY);
    }
    public static BigDecimal quantity(BigDecimal value) { return decimal(value,14,6,true,"Quantity"); }
    public static BigDecimal waste(BigDecimal value) {
        BigDecimal d = decimal(value,4,6,false,"Waste factor");
        if (d.compareTo(TEN)>0) throw new IllegalArgumentException("Waste factor exceeds 10.");
        return d;
    }
    public static BigDecimal price(BigDecimal value) { return decimal(value,18,2,true,"Amount"); }
    public static void validateStatistics(BigDecimal min, BigDecimal max, BigDecimal average, BigDecimal median) {
        min=price(min); max=price(max); average=price(average); median=price(median);
        if (min.compareTo(max)>0 || average.compareTo(min)<0 || average.compareTo(max)>0
                || median.compareTo(min)<0 || median.compareTo(max)>0)
            throw new IllegalArgumentException("Inconsistent material price statistics.");
    }
    public static BigDecimal effectiveQuantity(BigDecimal base, BigDecimal waste, BigDecimal requested) {
        return quantity(quantity(base).multiply(BigDecimal.ONE.add(waste(waste)))
                .multiply(quantity(requested)).setScale(6, ROUND));
    }
    public static BigDecimal lineTotal(BigDecimal effective, BigDecimal unitPrice) {
        return price(quantity(effective).multiply(price(unitPrice)).setScale(2, ROUND));
    }
    public static BigDecimal addLine(BigDecimal subtotal, BigDecimal line) {
        return price(decimal(subtotal,18,2,false,"Subtotal").add(price(line)));
    }
    public static BigDecimal unitCost(BigDecimal total, BigDecimal requested) {
        return price(price(total).divide(quantity(requested),2,ROUND));
    }
}
