package com.sakhtyar.material.application;

import static com.sakhtyar.material.api.MaterialDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.knowledge.domain.*;
import com.sakhtyar.material.domain.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class Phase18MaterialServiceTest {
    final MaterialItemRepository items=mock(MaterialItemRepository.class);
    final MaterialVariantRepository variants=mock(MaterialVariantRepository.class);
    final MaterialPriceObservationRepository observations=mock(MaterialPriceObservationRepository.class);
    final KnowledgeSourceRepository sources=mock(KnowledgeSourceRepository.class);
    final KnowledgeTermRepository terms=mock(KnowledgeTermRepository.class);
    final AuditService audit=mock(AuditService.class);
    final MaterialService service=new MaterialService(items,variants,observations,mock(MaterialPriceAggregateRepository.class),
        mock(MaterialPriceAnomalyRepository.class),sources,terms,new PriceAnomalyDetector(),audit);
    final UUID id=UUID.randomUUID();
    MaterialItemEntity item(boolean active){return new MaterialItemEntity(id,"TEST-STEEL","میلگرد",null,"STEEL","KG",null,active,Map.of(),"test",Instant.EPOCH);}
    UpsertMaterialRequest req(String unit){return new UpsertMaterialRequest("TEST-STEEL","ميلگرد",null,"STEEL",unit,null,true,Map.of());}
    @Test void lockedUpdateCannotReinterpretBaseUnit(){
        var original=item(true); when(items.findLockedById(id)).thenReturn(Optional.of(original));
        assertThrows(IllegalArgumentException.class,()->service.updateMaterial(id,req("TON")));
        assertEquals("KG",original.getUnitCode()); verifyNoInteractions(audit);
    }
    @Test void validUpdateNormalizesPersianAndPreservesIdentity(){
        var original=item(true);when(items.findLockedById(id)).thenReturn(Optional.of(original));
        var result=service.updateMaterial(id,req(" kg "));
        assertEquals("میلگرد",result.nameFa()); assertEquals("KG",result.unitCode());
    }
    @Test void inactiveCatalogCannotAcceptPrices(){
        when(items.findLockedById(id)).thenReturn(Optional.of(item(false)));
        assertThrows(IllegalArgumentException.class,()->service.createObservation(id,null,null));
        verifyNoInteractions(sources,observations,audit);
    }
    @Test void variantMustBelongToCatalogItem(){
        UUID vid=UUID.randomUUID();when(items.findLockedById(id)).thenReturn(Optional.of(item(true)));
        var variant=new MaterialVariantEntity(vid,UUID.randomUUID(),null,null,null,null,null,Map.of(),true,"test",Instant.EPOCH);
        when(variants.findById(vid)).thenReturn(Optional.of(variant));
        var req=new CreateVariantRequest(null,null,null,null,null,Map.of(),false);
        assertThrows(IllegalArgumentException.class,()->service.updateVariant(id,vid,req));verifyNoInteractions(audit);
    }
    @Test void referencedVariantCanBeArchivedButNotReinterpreted(){
        UUID vid=UUID.randomUUID();when(items.findLockedById(id)).thenReturn(Optional.of(item(true)));
        var variant=new MaterialVariantEntity(vid,id,"OLD",null,null,null,null,Map.of(),true,"test",Instant.EPOCH);
        when(variants.findById(vid)).thenReturn(Optional.of(variant));
        var observation=mock(MaterialPriceObservationEntity.class); when(observation.getVariantId()).thenReturn(vid);
        when(observations.findByMaterialItemIdOrderByObservedAtDesc(id)).thenReturn(List.of(observation));
        assertThrows(IllegalArgumentException.class,()->service.updateVariant(id,vid,new CreateVariantRequest("NEW",null,null,null,null,Map.of(),true)));
        assertFalse(service.updateVariant(id,vid,new CreateVariantRequest("OLD",null,null,null,null,Map.of(),false)).active());
    }
}
