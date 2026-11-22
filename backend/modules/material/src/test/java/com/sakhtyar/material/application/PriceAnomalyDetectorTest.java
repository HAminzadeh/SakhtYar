package com.sakhtyar.material.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.material.domain.PriceAnomalySeverity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceAnomalyDetectorTest {

    private final PriceAnomalyDetector detector = new PriceAnomalyDetector();

    @Test
    void flagsLargeDeviationFromHistoricalMedian() {
        var result = detector.detect(
                new BigDecimal("250"),
                List.of(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"))
        );

        assertTrue(result.isPresent());
        assertEquals(PriceAnomalySeverity.HIGH, result.orElseThrow().severity());
    }

    @Test
    void ignoresNormalVariation() {
        var result = detector.detect(
                new BigDecimal("110"),
                List.of(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"))
        );

        assertTrue(result.isEmpty());
    }
}