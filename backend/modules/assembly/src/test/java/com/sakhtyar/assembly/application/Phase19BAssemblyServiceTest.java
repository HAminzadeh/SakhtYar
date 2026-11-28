package com.sakhtyar.assembly.application;

import static com.sakhtyar.assembly.api.AssemblyDtos.*;
import com.sakhtyar.assembly.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.material.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual service, synthetic repositories. Physical PostgreSQL/HTTP acceptance is separate. */
class Phase19BAssemblyServiceTest {
    static BigDecimal d(String s){return new BigDecimal(s);}
    final UUID aid=UUID.randomUUID(), mid=UUID.randomUUID(), cid=UUID.randomUUID(), key=UUID.randomUUID();
    final Instant now=Instant.now().minusSeconds(60);
    final CostAssemblyRepository assemblies=mock(CostAssemblyRepository.class);
    final CostAssemblyComponentRepository components=mock(CostAssemblyComponentRepository.class);
    final CostEstimateRepository estimates=mock(CostEstimateRepository.class);
    final CostEstimateLineRepository lines=mock(CostEstimateLineRepository.class);
    final MaterialItemRepository materials=mock(MaterialItemRepository.class);
    final MaterialVariantRepository variants=mock(MaterialVariantRepository.class);
    final MaterialPriceAggregateRepository prices=mock(MaterialPriceAggregateRepository.class);
    final AuditService audit=mock(AuditService.class);
    final Authentication actor=mock(Authentication.class);
    final AssemblyService service=new AssemblyService(assemblies,components,estimates,lines,materials,variants,prices,audit);
    final CostAssemblyEntity assembly=new CostAssemblyEntity(aid,"TEST-P19B","TEST",null,"TEST","M2",null,true,Map.of(),"test",now);
    final CostAssemblyComponentEntity component=new CostAssemblyComponentEntity(cid,aid,mid,null,d("2"),d("0.10"),"KG",PriceBasis.MEDIAN,0,null,now);
    @BeforeEach void setup(){
        when(actor.getName()).thenReturn("test-actor");when(actor.isAuthenticated()).thenReturn(true);
        when(assemblies.findLockedById(aid)).thenReturn(Optional.of(assembly));
        when(components.findByAssemblyIdOrderBySortOrderAscIdAsc(aid)).thenReturn(List.of(component));
        var material=mock(MaterialItemEntity.class);when(material.isActive()).thenReturn(true);
        when(materials.findLockedById(mid)).thenReturn(Optional.of(material));
        when(lines.save(any(CostEstimateLineEntity.class))).thenAnswer(i->i.getArgument(0));
    }
    CalculateCostRequest request(String qty){return new CalculateCostRequest(d(qty),MaterialPriceType.RETAIL,"TOMAN","تهران","تهران");}
    MaterialPriceAggregateEntity ref(String amount){
        BigDecimal p=d(amount);var a=new MaterialPriceAggregateEntity(UUID.randomUUID(),mid,null,MaterialPriceType.RETAIL,"TOMAN","KG","تهران","تهران",1,p,p,p,p,now,now,"2.0",now);
        a.publish(List.of(UUID.randomUUID().toString()),now.plusSeconds(3600),"a".repeat(64));return a;
    }
    CostEstimateEntity old(UUID id,UUID k){
        var e=new CostEstimateEntity(id,aid,d("5"),"M2",MaterialPriceType.RETAIL,"TOMAN","تهران","تهران",d("66000.00"),d("330000.00"),"2.0","test-actor",now);
        e.bindCalculationRequest(k,null);return e;
    }
    @Test void retryReturnsSameIdWithoutRepricingOrWriting(){
        var existing=old(UUID.randomUUID(),key);
        when(estimates.findByAssemblyIdAndCalculatedByAndRequestKey(aid,"test-actor",key)).thenReturn(Optional.of(existing));
        when(lines.findByEstimateIdOrderByIdAsc(existing.getId())).thenReturn(List.of());
        var response=service.calculate(aid,request("5.000000"),actor,key);
        assertEquals(existing.getId(),response.id());assertEquals(d("330000.00"),response.totalCost());
        verifyNoInteractions(prices,materials,components,audit);verify(estimates,never()).save(any());verify(lines,never()).save(any());
    }
    @Test void sameKeyWithDifferentPayloadFailsClosed(){
        when(estimates.findByAssemblyIdAndCalculatedByAndRequestKey(aid,"test-actor",key)).thenReturn(Optional.of(old(UUID.randomUUID(),key)));
        assertThrows(IllegalStateException.class,()->service.calculate(aid,request("6"),actor,key));
        verifyNoInteractions(prices,materials,components,audit);verify(estimates,never()).save(any());
    }
    @Test void authenticatedKeyRequired(){
        assertThrows(IllegalArgumentException.class,()->service.calculate(aid,request("5"),null,key));
        verifyNoInteractions(estimates,prices,materials,components,audit);
    }
    @Test void twoComponentsUsingSameMaterialReadRatesOnce(){
        var second=new CostAssemblyComponentEntity(UUID.randomUUID(),aid,mid,null,d("1"),d("0"),"KG",PriceBasis.MIN,1,null,now);
        when(components.findByAssemblyIdOrderBySortOrderAscIdAsc(aid)).thenReturn(List.of(component,second));
        when(prices.findByMaterialItemIdOrderByCalculatedAtDesc(mid)).thenReturn(List.of(ref("30000.00")));
        when(estimates.findByAssemblyIdAndCalculatedByAndRequestKey(aid,"test-actor",key)).thenReturn(Optional.empty());
        var result=service.calculate(aid,request("5"),actor,key);
        assertEquals(d("480000.00"),result.totalCost());assertEquals(d("96000.00"),result.unitCost());
        assertEquals(2,result.lines().size());verify(prices,times(1)).findByMaterialItemIdOrderByCalculatedAtDesc(mid);
        verify(estimates).save(argThat(e->key.equals(e.getRequestKey())));
    }
    @Test void recalculationUsesNewPriceAndPersistsOrigin(){
        UUID previousId=UUID.randomUUID();var previous=old(previousId,null);
        when(estimates.findById(previousId)).thenReturn(Optional.of(previous));
        when(prices.findByMaterialItemIdOrderByCalculatedAtDesc(mid)).thenReturn(List.of(ref("40000.00")));
        var result=service.recalculate(previousId,actor,null);
        assertNotEquals(previousId,result.id());assertEquals(d("440000.00"),result.totalCost());
        assertEquals(d("330000.00"),previous.getTotalCost());
        verify(estimates).save(argThat(e->previousId.equals(e.getRecalculatedFromId())));
    }
    @Test void missingPriceDoesNotWriteEstimate(){
        when(prices.findByMaterialItemIdOrderByCalculatedAtDesc(mid)).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->service.calculate(aid,request("5"),actor));
        verifyNoInteractions(estimates,lines,audit);
    }
    @Test void scenarioLocksAssembliesThenMaterialsInGlobalOrder(){
        UUID low=new UUID(0,1), high=new UUID(0,2), mlow=new UUID(0,3), mhigh=new UUID(0,4);
        when(assemblies.findLockedById(low)).thenReturn(Optional.of(assembly));when(assemblies.findLockedById(high)).thenReturn(Optional.of(assembly));
        when(components.findByAssemblyIdOrderBySortOrderAscIdAsc(low)).thenReturn(List.of(new CostAssemblyComponentEntity(UUID.randomUUID(),low,mhigh,null,d("1"),d("0"),"KG",PriceBasis.MIN,0,null,now)));
        when(components.findByAssemblyIdOrderBySortOrderAscIdAsc(high)).thenReturn(List.of(new CostAssemblyComponentEntity(UUID.randomUUID(),high,mlow,null,d("1"),d("0"),"KG",PriceBasis.MIN,0,null,now)));
        var m=mock(MaterialItemEntity.class);when(m.isActive()).thenReturn(true);
        when(materials.findLockedById(mlow)).thenReturn(Optional.of(m));when(materials.findLockedById(mhigh)).thenReturn(Optional.of(m));
        service.lockForScenarioCalculation(List.of(high,low,high));
        var order=inOrder(assemblies,materials);order.verify(assemblies).findLockedById(low);order.verify(assemblies).findLockedById(high);
        order.verify(materials).findLockedById(mlow);order.verify(materials).findLockedById(mhigh);
        verifyNoInteractions(estimates,lines,prices,audit);
    }
}
