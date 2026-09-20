package com.sakhtyar.agents.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AgentNumbers {

    private AgentNumbers() {
    }

    public static BigDecimal normalizeRatio(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return value.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        }
        return value;
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal area(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }
}
