package com.sakhtyar.scenario.application;

import com.sakhtyar.assembly.application.AssemblyService;
import com.sakhtyar.assembly.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.material.domain.*;
import com.sakhtyar.scenario.api.ScenarioDtos.CalculateScenarioRequest;
import com.sakhtyar.scenario.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real service-to-service calculations; repository mocks only. No REAL data or DB access. */
class Phase18CCostChainTest {
    final UUID mid=UUID.randomUUID(), aid=UUID.randomUUID(), sid=UUID.randomUUID();
    final Instant now=Instant.now().minusSeconds(60);
    final CostAssemblyRepository assemblies=mock(CostAssemblyRepository.class);
    final CostAssemblyComponentRepository components=mock(CostAssemblyComponentRepository.class);
    final CostEstimateRepository estimates=mock(CostEstimateRepository.class);
    final CostEstimateLineRepository estimateLines=mock(CostEstimateLineRepository.class);
    final MaterialItemRepository materials=mock(MaterialItemRepository.class);
    final MaterialVariantRepository variants=mock(MaterialVariantRepository.class);
    final MaterialPriceAggregateRepository prices=mock(MaterialPriceAggregateRepository.class);
    final ConstructionScenarioRepository scenarios=mock(ConstructionScenarioRepository.class);
    final ConstructionScenarioItemRepository items=mock(ConstructionScenarioItemRepository.class);
    final ScenarioCostSnapshotRepository snapshots=mock(ScenarioCostSnapshotRepository.class);
    final ScenarioCostSnapshotLineRepository snapshotLines=mock(ScenarioCostSnapshotLineRepository.class);
    final AuditService audit=mock(AuditService.class);
    final AssemblyService assemblyService=new AssemblyService(assemblies,components,estimates,estimateLines,materials,variants,prices,audit);
    final ScenarioService service=new ScenarioService(mock(QualityPackageRepository.class),mock(QualityPackageSelectionRepository.class),scenarios,items,snapshots,snapshotLines,assemblies,assemblyService,audit);
    @BeforeEach void setup(){
        var assembly=mock(CostAssemblyEntity.class); when(assembly.isActive()).thenReturn(true);when(assembly.getOutputUnitCode()).thenReturn("M2");
        when(assemblies.findLockedById(aid)).thenReturn(Optional.of(assembly));
        var material=mock(MaterialItemEntity.class);when(material.isActive()).thenReturn(true);when(materials.findLockedById(mid)).thenReturn(Optional.of(material));
        var component=new CostAssemblyComponentEntity(UUID.randomUUID(),aid,mid,null,new BigDecimal("2"),new BigDecimal("0.10"),"KG",PriceBasis.MEDIAN,0,null,now);
        when(components.findByAssemblyIdOrderBySortOrderAscIdAsc(aid)).thenReturn(List.of(component));
        var scenario=mock(ConstructionScenarioEntity.class);when(scenario.isActive()).thenReturn(true);when(scenario.getStatus()).thenReturn(ScenarioStatus.READY);
        when(scenarios.findLockedById(sid)).thenReturn(Optional.of(scenario));
        var item=new ConstructionScenarioItemEntity(UUID.randomUUID(),sid,null,aid,new BigDecimal("5"),ScenarioItemSource.MANUAL,true,0,null,now);
        when(items.findByScenarioIdOrderBySortOrderAscIdAsc(sid)).thenReturn(List.of(item));
        when(estimateLines.save(any(CostEstimateLineEntity.class))).thenAnswer(inv->inv.getArgument(0));
        when(snapshotLines.save(any(ScenarioCostSnapshotLineEntity.class))).thenAnswer(inv->inv.getArgument(0));
    }
    MaterialPriceAggregateEntity ref(String amount){
        BigDecimal p=new BigDecimal(amount);var a=new MaterialPriceAggregateEntity(UUID.randomUUID(),mid,null,MaterialPriceType.RETAIL,"TOMAN","KG","تهران","تهران",1,p,p,p,p,now,now,"2.0",now);
        a.publish(List.of(UUID.randomUUID().toString()),now.plusSeconds(3600),"a".repeat(64));return a;
    }
    CalculateScenarioRequest request(){return new CalculateScenarioRequest(MaterialPriceType.RETAIL,"TOMAN","تهران","تهران");}
    @Test void governedPriceFlowsThroughRealServicesAndOldResultIsPreserved(){
        var first=ref("30000.00");var second=ref("40000.00");
        when(prices.findByMaterialItemIdOrderByCalculatedAtDesc(mid)).thenReturn(List.of(first),List.of(second));
        var before=service.calculate(sid,request(),null);var after=service.calculate(sid,request(),null);
        assertEquals(new BigDecimal("330000.00"),before.totalCost());assertEquals(new BigDecimal("440000.00"),after.totalCost());
        assertEquals("2.0",before.algorithmVersion());assertNotEquals(before.id(),after.id());assertNotEquals(before.lines().getFirst().assemblyEstimateId(),after.lines().getFirst().assemblyEstimateId());
        assertEquals(new BigDecimal("330000.00"),before.lines().getFirst().lineTotal());
        verify(estimates,times(2)).save(any(CostEstimateEntity.class));verify(snapshots,times(2)).save(any(ScenarioCostSnapshotEntity.class));
    }
    @Test void missingEligiblePriceStopsBeforeAnySnapshotWrite(){
        when(prices.findByMaterialItemIdOrderByCalculatedAtDesc(mid)).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->service.calculate(sid,request(),null));
        verifyNoInteractions(estimates,estimateLines,snapshots,snapshotLines);
    }
}
