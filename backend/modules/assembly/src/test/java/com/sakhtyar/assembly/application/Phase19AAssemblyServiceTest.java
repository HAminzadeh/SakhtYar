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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase19AAssemblyServiceTest {
    final UUID id=UUID.randomUUID(), componentId=UUID.randomUUID(), materialId=UUID.randomUUID();
    final CostAssemblyRepository assemblies=mock(CostAssemblyRepository.class);
    final CostAssemblyComponentRepository components=mock(CostAssemblyComponentRepository.class);
    final CostEstimateLineRepository lines=mock(CostEstimateLineRepository.class);
    final MaterialItemRepository materials=mock(MaterialItemRepository.class);
    final MaterialVariantRepository variants=mock(MaterialVariantRepository.class);
    final AuditService audit=mock(AuditService.class);
    final AssemblyService service=new AssemblyService(assemblies,components,mock(CostEstimateRepository.class),lines,
            materials,variants,mock(MaterialPriceAggregateRepository.class),audit);
    final CostAssemblyEntity assembly=new CostAssemblyEntity(id,"TEST-19A","نمونه",null,"TEST","M2",null,true,Map.of(),"test",Instant.EPOCH);
    final CostAssemblyComponentEntity component=new CostAssemblyComponentEntity(componentId,id,materialId,null,BigDecimal.ONE,
            BigDecimal.ZERO,"KG",PriceBasis.MEDIAN,0,null,Instant.EPOCH);
    @BeforeEach void setup(){
        when(assemblies.findLockedById(id)).thenReturn(Optional.of(assembly));
        when(components.findById(componentId)).thenReturn(Optional.of(component));
        MaterialItemEntity m=mock(MaterialItemEntity.class);
        when(m.getId()).thenReturn(materialId);when(m.isActive()).thenReturn(true);when(m.getUnitCode()).thenReturn("KG");
        when(materials.findLockedById(materialId)).thenReturn(Optional.of(m));
    }
    CreateComponentRequest request(String unit,BigDecimal qty){return new CreateComponentRequest(materialId,null,qty,new BigDecimal("0.1"),unit,PriceBasis.MAX,4,"test");}
    @Test void updatesComponentInPlaceAndAudits(){
        var result=service.updateComponent(id,componentId,request("kg",new BigDecimal("2")));
        assertEquals(componentId,result.id());assertEquals(new BigDecimal("2.000000"),result.quantity());assertEquals(PriceBasis.MAX,result.priceBasis());
        verify(audit).record(eq("COST_ASSEMBLY"),eq(id),eq("ASSEMBLY_COMPONENT_UPDATED"),anyMap());
        verify(components,never()).delete(any());
    }
    @Test void rejectsInvalidUnitAndNegativeWithoutMutating(){
        assertThrows(IllegalArgumentException.class,()->service.updateComponent(id,componentId,request("M2",BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class,()->service.updateComponent(id,componentId,request("KG",BigDecimal.ONE.negate())));
        assertEquals(BigDecimal.ONE,component.getQuantity());verifyNoInteractions(audit);
    }
    @Test void referencedComponentCannotBeDeleted(){
        when(lines.existsByAssemblyComponentId(componentId)).thenReturn(true);
        assertThrows(IllegalStateException.class,()->service.removeComponent(id,componentId));verify(components,never()).delete(any());verifyNoInteractions(audit);
    }
    @Test void unreferencedComponentCanBeDeleted(){
        when(lines.existsByAssemblyComponentId(componentId)).thenReturn(false);
        service.removeComponent(id,componentId);verify(components).delete(component);
    }
    @Test void blocksForeignComponent(){
        UUID foreign=UUID.randomUUID();when(assemblies.findLockedById(foreign)).thenReturn(Optional.of(assembly));
        assertThrows(IllegalArgumentException.class,()->service.updateComponent(foreign,componentId,request("KG",BigDecimal.ONE)));
        verifyNoInteractions(materials,audit);
    }
    @Test void preventsOutputUnitReinterpretation(){
        var r=new UpsertAssemblyRequest("TEST-19A","نمونه",null,"TEST","M3",null,true,Map.of());
        assertThrows(IllegalArgumentException.class,()->service.updateAssembly(id,r));assertEquals("M2",assembly.getOutputUnitCode());verifyNoInteractions(audit);
    }
}
