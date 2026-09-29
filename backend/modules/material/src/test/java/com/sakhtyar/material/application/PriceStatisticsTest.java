package com.sakhtyar.material.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceStatisticsTest {

    @Test
    void calculatesOddMedianAndAverage() {
        PriceStatistics.Stats stats = PriceStatistics.calculate(List.of(
                new BigDecimal("100"),
                new BigDecimal("300"),
                new BigDecimal("200")
        ));

        assertEquals(new BigDecimal("100.00"), stats.min());
        assertEquals(new BigDecimal("300.00"), stats.max());
        assertEquals(new BigDecimal("200.00"), stats.average());
        assertEquals(new BigDecimal("200.00"), stats.median());
    }

    @Test
    void calculatesEvenMedian() {
        PriceStatistics.Stats stats = PriceStatistics.calculate(List.of(
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("300"),
                new BigDecimal("400")
        ));

        assertEquals(new BigDecimal("250.00"), stats.median());
    }
}