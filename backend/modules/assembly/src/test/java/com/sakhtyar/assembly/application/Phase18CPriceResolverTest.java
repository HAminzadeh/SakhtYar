package com.sakhtyar.assembly.application;

import com.sakhtyar.material.domain.MaterialPriceAggregateEntity;
import com.sakhtyar.material.domain.MaterialPriceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase18CPriceResolverTest {
    static final Instant NOW=Instant.parse("2026-01-10T00:00:00Z");
    static final UUID ITEM=UUID.randomUUID();
    MaterialPriceAggregateEntity publication(String version, Instant expiry, String currency, String province, UUID item) {
        var a=new MaterialPriceAggregateEntity(UUID.randomUUID(),item,null,MaterialPriceType.RETAIL,currency,"KG",province,"تهران",1,
            new BigDecimal("30000.00"),new BigDecimal("30000.00"),new BigDecimal("30000.00"),new BigDecimal("30000.00"),
            NOW.minusSeconds(600),NOW.minusSeconds(600),version,NOW.minusSeconds(300));
        a.publish(List.of(UUID.randomUUID().toString()),expiry,"a".repeat(64));return a;
    }
    MaterialPriceAggregateEntity resolve(List<MaterialPriceAggregateEntity> rows){return MaterialPriceResolver.resolve(rows,ITEM,null,MaterialPriceType.RETAIL,"TOMAN","KG","تهران","تهران",NOW);}
    @Test void selectsNormalizedRegionAndExactDenomination(){var a=publication("2.0",NOW.plusSeconds(1),"TOMAN","تهران",ITEM);assertSame(a,resolve(List.of(a)));}
    @Test void rejectsExpiredAndLegacy(){assertThrows(IllegalStateException.class,()->resolve(List.of(publication("2.0",NOW,"TOMAN","تهران",ITEM),publication("1.0",NOW.plusSeconds(1),"TOMAN","تهران",ITEM))));}
    @Test void rejectsForeignItemCurrencyOrMarket(){assertThrows(IllegalStateException.class,()->resolve(List.of(publication("2.0",NOW.plusSeconds(1),"IRR","تهران",ITEM),publication("2.0",NOW.plusSeconds(1),"TOMAN","قم",ITEM),publication("2.0",NOW.plusSeconds(1),"TOMAN","تهران",UUID.randomUUID()))));}
    @Test void rejectsAmbiguousPublication(){var a=publication("2.0",NOW.plusSeconds(1),"TOMAN","تهران",ITEM);assertThrows(IllegalStateException.class,()->resolve(List.of(a,a)));}
    @Test void historicalCostIndependentOfNewReference(){var a=publication("2.0",NOW.plusSeconds(1),"TOMAN","تهران",ITEM);var qty=AssemblyCostCalculator.effectiveQuantity(new BigDecimal("2"),new BigDecimal("0.10"),new BigDecimal("5"));var old=AssemblyCostCalculator.lineTotal(qty,a.getMedianAmount());assertEquals(new BigDecimal("330000.00"),old);assertEquals(new BigDecimal("440000.00"),AssemblyCostCalculator.lineTotal(qty,new BigDecimal("40000")));assertEquals(new BigDecimal("330000.00"),old);}
}
