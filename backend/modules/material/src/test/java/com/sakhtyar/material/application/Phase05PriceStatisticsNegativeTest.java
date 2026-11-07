package com.sakhtyar.material.application;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase05PriceStatisticsNegativeTest {
    @Test void rejectsNegativeOrNullObservations() {
        assertThrows(IllegalArgumentException.class,()->PriceStatistics.calculate(List.of(
            new BigDecimal("125"),new BigDecimal("-1"))));
        assertThrows(IllegalArgumentException.class,()->PriceStatistics.calculate(Arrays.asList(
            new BigDecimal("125"),null)));
    }
    @Test void preservesPositiveGoldenMedian() {
        assertEquals(new BigDecimal("250.00"),PriceStatistics.calculate(List.of(
            new BigDecimal("100"),new BigDecimal("200"),
            new BigDecimal("300"),new BigDecimal("400"))).median());
    }
}
