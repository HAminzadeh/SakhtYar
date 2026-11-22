package com.sakhtyar.analysis.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase05NumericSnapshotTest {
    private final UUID w=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID r=UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID c=UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final Instant instant=Instant.parse("2026-01-01T00:00:00Z");
    private static BigDecimal d(String v){return new BigDecimal(v);}
    private Phase05NumericSnapshot freeze(Map<String,BigDecimal> in,Map<String,BigDecimal> out){
        return Phase05NumericSnapshot.freeze(w,r,c,"IRR","price-v1","rule-v1","algo-v1",instant,in,out);
    }

    @Test void orderAndDecimalScaleDoNotChangeHash(){
        var a=freeze(Map.of("cost",d("20.00"),"areaM2",d("100")),Map.of("profit",d("3.50")));
        var b=freeze(Map.of("areaM2",d("100.0"),"cost",d("20")),Map.of("profit",d("3.500")));
        assertEquals(a.envelopeHash(),b.envelopeHash());
        assertTrue(a.verify());
    }
    @Test void metadataAndWorkspaceChangesInvalidateHash(){
        var a=freeze(Map.of("cost",d("20")),Map.of("profit",d("3")));
        var other=Phase05NumericSnapshot.freeze(w,UUID.randomUUID(),c,"IRR","price-v1","rule-v1","algo-v1",
                instant,Map.of("cost",d("20")),Map.of("profit",d("3")));
        assertNotEquals(a.envelopeHash(),other.envelopeHash());
        var otherCurrency=Phase05NumericSnapshot.freeze(w,r,c,"TOMAN","price-v1","rule-v1","algo-v1",
                instant,Map.of("cost",d("20")),Map.of("profit",d("3")));
        assertNotEquals(a.envelopeHash(),otherCurrency.envelopeHash());
    }
    @Test void immutableDefensiveCopyAndTamperDetection(){
        var inputs=new HashMap<String,BigDecimal>();inputs.put("cost",d("10"));
        var a=freeze(inputs,Map.of("profit",d("5")));
        inputs.put("cost",d("15"));
        assertEquals(0,d("10").compareTo(a.inputs().get("cost")));
        assertThrows(UnsupportedOperationException.class,()->a.inputs().put("evil",d("1")));
        var forged=new Phase05NumericSnapshot(a.workspaceId(),a.runId(),a.caseId(),a.currencyUnit(),a.priceVersion(),
                a.ruleVersion(),a.algorithmVersion(),a.createdAt(),Map.of("cost",d("99")),a.outputs(),
                a.inputHash(),a.outputHash(),a.envelopeHash());
        assertFalse(forged.verify());
    }
    @Test void rejectsAmbiguousUnitsAndSensitiveTextKeys(){
        assertThrows(IllegalArgumentException.class,()->Phase05NumericSnapshot.freeze(w,r,c,"USD", "p","r","v",instant,
                Map.of("cost",d("2")),Map.of("profit",d("1"))));
        assertThrows(IllegalArgumentException.class,()->freeze(Map.of("user-email",d("2")),Map.of("profit",d("1"))));
        assertThrows(IllegalArgumentException.class,()->freeze(Map.of("cost",d("1E+35")),Map.of("profit",d("1"))));
    }
}