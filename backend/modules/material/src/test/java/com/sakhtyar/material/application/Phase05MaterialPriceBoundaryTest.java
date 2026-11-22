package com.sakhtyar.material.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class Phase05MaterialPriceBoundaryTest {
    private static BigDecimal d(String v){return new BigDecimal(v);}
    @Test void negativeCandidateCannotBecomeHighSeverityPriceAnomaly(){
        assertThrows(IllegalArgumentException.class,()->new PriceAnomalyDetector().detect(d("-100"),
                List.of(d("100"),d("100"),d("100"))));
    }
    @Test void invalidHistoryRejectedEvenBelowAnomalyMinimum(){
        assertThrows(IllegalArgumentException.class,()->new PriceAnomalyDetector().detect(d("100"),
                List.of(d("100"),d("-1"))));
    }
    @Test void independentMedianDeviationContract(){
        var result=new PriceAnomalyDetector().detect(d("240"),List.of(d("100"),d("110"),d("90")));
        assertTrue(result.isPresent());
        assertEquals(d("1.4000"),result.orElseThrow().score());
    }
}