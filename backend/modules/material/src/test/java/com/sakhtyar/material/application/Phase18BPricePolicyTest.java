package com.sakhtyar.material.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class Phase18BPricePolicyTest {
    static BigDecimal d(String v){return new BigDecimal(v);}
    @Test void normalizesAmountQuantityUnitAndIranianCurrency(){
        assertEquals(0,d("30000").compareTo(MaterialPricePolicy.unitPrice(d("1500000"),d("50"),"KG","KG","TOMAN","TOMAN")));
        assertEquals(0,d("320000").compareTo(MaterialPricePolicy.unitPrice(d("32000000"),d("0.1"),"TON","KG","IRR","IRR")));
        assertEquals(0,d("300000").compareTo(MaterialPricePolicy.unitPrice(d("1500000"),d("50"),"KG","KG","TOMAN","IRR")));
        assertEquals(0,d("32000").compareTo(MaterialPricePolicy.unitPrice(d("32000000"),d("100"),"KG","KG","IRR","TOMAN")));
    }
    @Test void failsClosedForUnverifiedConversionAndStorageOverflow(){
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.unitPrice(d("10"),d("1"),"BAG","KG","IRR","IRR"));
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.currencyAmount(d("10"),"USD","IRR"));
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.unitPrice(d("0.01"),d("100"),"KG","KG","IRR","TOMAN"));
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.unitPrice(d("999999999999999999.99"),d("0.000001"),"KG","KG","TOMAN","IRR"));
    }
    @Test void freshnessIsHalfOpenAndFutureRejected(){
        Instant now=Instant.parse("2026-11-28T00:00:00Z");
        assertTrue(MaterialPricePolicy.fresh(now,now));
        assertFalse(MaterialPricePolicy.fresh(now.minus(MaterialPricePolicy.MAX_AGE),now));
        assertTrue(MaterialPricePolicy.fresh(now.minus(MaterialPricePolicy.MAX_AGE).plusSeconds(1),now));
        assertFalse(MaterialPricePolicy.fresh(now.plusSeconds(1),now));
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.observationTime(now.plusSeconds(1),now));
        assertEquals(Instant.parse("2026-11-27T00:00:00.123456Z"),MaterialPricePolicy.observationTime(Instant.parse("2026-11-27T00:00:00.123456789Z"),now));
    }
    @Test void confidenceAndSourceEvidenceAreExplicit(){
        assertTrue(MaterialPricePolicy.confident(d("0.50")));
        assertFalse(MaterialPricePolicy.confident(d("0.49")));
        assertFalse(MaterialPricePolicy.confident(null));
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.sourceUrl(null,true));
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.sourceUrl("file:///tmp/price",false));
        assertThrows(IllegalArgumentException.class,()->MaterialPricePolicy.sourceUrl("https://user:pw@example.invalid/price",false));
        assertEquals("https://example.invalid/price",MaterialPricePolicy.sourceUrl("https://example.invalid/price",true));
    }
    @Test void anomalyThresholdUsesUnroundedMedianAndExactComparison(){
        var detector=new PriceAnomalyDetector();
        assertTrue(detector.detect(d("149.999"),java.util.List.of(d("100"),d("100"),d("100"))).isEmpty());
        assertEquals(com.sakhtyar.material.domain.PriceAnomalySeverity.MEDIUM,detector.detect(d("150"),java.util.List.of(d("100"),d("100"),d("100"))).orElseThrow().severity());
        assertEquals(com.sakhtyar.material.domain.PriceAnomalySeverity.HIGH,detector.detect(d("0.020"),java.util.List.of(d("0.010"),d("0.010"),d("0.010"))).orElseThrow().severity());
        assertEquals(0,d("0.0075").compareTo(MaterialPricePolicy.exactMedian(java.util.List.of(d("0.006"),d("0.009")))));
    }
    @Test void groupAndIdentityAreDeterministic(){
        assertEquals("IRR",MaterialPricePolicy.currencyGroup("TOMAN"));
        assertEquals("USD",MaterialPricePolicy.currencyGroup("USD"));
        assertEquals("تهران",MaterialPricePolicy.region(" تهران "));
        assertNotEquals(MaterialPricePolicy.digest("a","bc"),MaterialPricePolicy.digest("ab","c"));
        assertNotEquals(MaterialPricePolicy.digest((String)null),MaterialPricePolicy.digest(""));
        assertEquals(MaterialPricePolicy.digest("a","b"),MaterialPricePolicy.digest("a","b"));
    }
}
