package com.sakhtyar.material.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PriceStatistics {

    private PriceStatistics() {}

    public static Stats calculate(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("At least one price value is required.");
        }

        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());

        BigDecimal sum = sorted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(sorted.size()), 2, RoundingMode.HALF_UP);

        BigDecimal median;
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            median = sorted.get(middle - 1)
                    .add(sorted.get(middle))
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else {
            median = sorted.get(middle).setScale(2, RoundingMode.HALF_UP);
        }

        return new Stats(
                sorted.getFirst().setScale(2, RoundingMode.HALF_UP),
                sorted.getLast().setScale(2, RoundingMode.HALF_UP),
                average,
                median
        );
    }

    public record Stats(
            BigDecimal min,
            BigDecimal max,
            BigDecimal average,
            BigDecimal median
    ) {}
}