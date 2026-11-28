package com.sakhtyar.assembly.application;

import static com.sakhtyar.assembly.api.AssemblyDtos.*;
import com.sakhtyar.assembly.domain.*;
import com.sakhtyar.material.domain.MaterialPriceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase19BComparisonTest {
    static BigDecimal d(String s){return new BigDecimal(s);}
    final UUID assembly=UUID.randomUUID(), component=UUID.randomUUID(), material=UUID.randomUUID();
    EstimateLineResponse line(UUID id, String qty, String price,String total){return new EstimateLineResponse(UUID.randomUUID(),id,material,null,UUID.randomUUID(),d("2"),d("0.1"),d(qty),"KG",d(price),d(total),PriceBasis.MEDIAN,1,Instant.EPOCH,Instant.EPOCH);}
    EstimateResponse estimate(UUID a,String currency,String total,List<EstimateLineResponse> rows){return new EstimateResponse(UUID.randomUUID(),a,d("5"),"M2",MaterialPriceType.RETAIL,currency,"تهران","تهران",d(total).divide(d("5")),d(total),"2.0",CostEstimateStatus.CALCULATED,"test",Instant.EPOCH,rows);}
    @Test void preservesInputsAndExactDeltas(){
        var x=estimate(assembly,"TOMAN","330000.00",List.of(line(component,"11","30000","330000.00")));
        var y=estimate(assembly,"TOMAN","440000.00",List.of(line(component,"11","40000","440000.00")));
        var result=AssemblyEstimateComparison.compare(x,y);
        assertEquals("110000.00",result.totalDelta());assertEquals("22000.00",result.unitCostDelta());
        assertEquals("CHANGED",result.lines().getFirst().change());assertEquals("110000.00",result.lines().getFirst().delta());
        assertEquals(d("330000.00"),x.totalCost());
    }
    @Test void rejectsCurrencyOrAssemblyMismatch(){
        var x=estimate(assembly,"TOMAN","100.00",List.of());
        assertThrows(IllegalArgumentException.class,()->AssemblyEstimateComparison.compare(x,estimate(assembly,"IRR","100.00",List.of())));
        assertThrows(IllegalArgumentException.class,()->AssemblyEstimateComparison.compare(x,estimate(UUID.randomUUID(),"TOMAN","100.00",List.of())));
    }
    @Test void exposesAddedRemovedAndRejectsDuplicateLines(){
        UUID other=UUID.randomUUID();var a=line(component,"1","10","10.00");var b=line(other,"1","20","20.00");
        var x=estimate(assembly,"TOMAN","10.00",List.of(a));var y=estimate(assembly,"TOMAN","20.00",List.of(b));
        var result=AssemblyEstimateComparison.compare(x,y);
        assertEquals(Set.of("ADDED","REMOVED"),new HashSet<>(result.lines().stream().map(AssemblyEstimateComparison.Line::change).toList()));
        assertThrows(IllegalStateException.class,()->AssemblyEstimateComparison.compare(estimate(assembly,"TOMAN","20.00",List.of(a,a)),y));
    }
}
