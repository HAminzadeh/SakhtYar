package com.sakhtyar.material.application;

import static com.sakhtyar.material.api.MaterialDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.knowledge.domain.*;
import com.sakhtyar.material.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class Phase18BPriceServiceTest {
    final MaterialItemRepository items=mock(MaterialItemRepository.class);
    final MaterialVariantRepository variants=mock(MaterialVariantRepository.class);
    final MaterialPriceObservationRepository observations=mock(MaterialPriceObservationRepository.class);
    final MaterialPriceAggregateRepository aggregates=mock(MaterialPriceAggregateRepository.class);
    final MaterialPriceAnomalyRepository anomalies=mock(MaterialPriceAnomalyRepository.class);
    final AuditService audit=mock(AuditService.class);
    final MaterialService service=new MaterialService(items,variants,observations,aggregates,anomalies,
        mock(KnowledgeSourceRepository.class),mock(KnowledgeTermRepository.class),new PriceAnomalyDetector(),audit);
    final UUID id=UUID.randomUUID(), source=UUID.randomUUID();
    final Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
    final MaterialItemEntity item=new MaterialItemEntity(id,"TEST-STEEL","میلگرد",null,"STEEL","KG",null,true,Map.of(),"test",now);
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority("USER_MANAGE")));
    static BigDecimal d(String v){return new BigDecimal(v);}
    @BeforeEach void setup(){
        when(items.findLockedById(id)).thenReturn(Optional.of(item));
        when(items.findById(id)).thenReturn(Optional.of(item));
        when(observations.sourceMatchesMaterial(source,id)).thenReturn(true);
        when(observations.activeCurrency(anyString())).thenReturn(true);
        when(observations.findLegacyCandidates(eq(id),eq(source),any(Instant.class))).thenReturn(List.of());
        when(observations.findByMaterialItemIdAndQuoteKey(eq(id),anyString())).thenReturn(Optional.empty());
        when(observations.findWithin(eq(id),eq(KnowledgeReviewStatus.APPROVED),any(Instant.class),any(Instant.class))).thenReturn(List.of());
        when(aggregates.findByMaterialItemIdOrderByCalculatedAtDesc(id)).thenReturn(List.of());
        when(anomalies.findByObservationId(any(UUID.class))).thenReturn(Optional.empty());
        when(aggregates.saveAll(anyList())).thenAnswer(invocation->invocation.getArgument(0));
    }
    CreatePriceObservationRequest request(PriceObservationOrigin origin,String url){
        return new CreatePriceObservationRequest(null,MaterialPriceType.RETAIL,d("1500000"),"TOMAN","KG",d("50"),"تهران","تهران",source,url,now.minusSeconds(60),d("0.90"),origin,Map.of("fixture","synthetic"));
    }
    MaterialPriceObservationEntity quote(String amount,String currency,String quantity,long days){
        var o=new MaterialPriceObservationEntity(UUID.randomUUID(),id,null,MaterialPriceType.RETAIL,MaterialFoundation.amount(d(amount)),currency,"KG",MaterialFoundation.quantity(d(quantity)),"تهران","تهران",source,null,now.minus(days,ChronoUnit.DAYS),now,d("0.9000"),PriceObservationOrigin.ADMIN,Map.of(),"test");
        o.review(KnowledgeReviewStatus.APPROVED,"reviewer","TEST",now);return o;
    }
    @Test void exactReplayReturnsOriginalAndDoesNotWriteTwice(){
        var first=service.createObservation(id,request(PriceObservationOrigin.ADMIN,null),auth);
        var captor=ArgumentCaptor.forClass(MaterialPriceObservationEntity.class);
        verify(observations).save(captor.capture());
        when(observations.findByMaterialItemIdAndQuoteKey(eq(id),anyString())).thenReturn(Optional.of(captor.getValue()));
        var second=service.createObservation(id,request(PriceObservationOrigin.ADMIN,null),auth);
        assertEquals(first.id(),second.id());verify(observations,times(1)).save(any());
        assertThrows(IllegalArgumentException.class,()->service.createObservation(id,request(PriceObservationOrigin.ADMIN,"https://example.invalid/new"),auth));
    }
    @Test void sourceAndExternalProvenanceAreRequired(){
        assertThrows(IllegalArgumentException.class,()->service.createObservation(id,request(PriceObservationOrigin.CRAWLER,null),auth));
        when(observations.sourceMatchesMaterial(source,id)).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.createObservation(id,request(PriceObservationOrigin.ADMIN,null),auth));
        verify(observations,never()).save(any());
    }
    @Test void aggregatesNormalizeAndExcludeExpiredAndOpenAnomalies(){
        var a=quote("1500000","TOMAN","50",2);var b=quote("32000000","IRR","100",1);
        var stale=quote("99999999","IRR","1",31);var flagged=quote("90000000","IRR","1",3);
        var anomaly=new MaterialPriceAnomalyEntity(UUID.randomUUID(),flagged.getId(),"MEDIAN_DEVIATION",PriceAnomalySeverity.HIGH,d("2.0000"),"TEST",now);
        when(anomalies.findByObservationId(flagged.getId())).thenReturn(Optional.of(anomaly));
        when(observations.findWithin(eq(id),eq(KnowledgeReviewStatus.APPROVED),any(Instant.class),any(Instant.class))).thenReturn(List.of(a,b,stale,flagged));
        var result=service.rebuildAggregates(id);
        assertEquals(2,result.size());
        var toman=result.stream().filter(r->r.currencyCode().equals("TOMAN")).findFirst().orElseThrow();
        assertEquals(2,toman.sampleCount());assertEquals(d("31000.00"),toman.averageAmount());assertEquals(d("31000.00"),toman.medianAmount());
        assertEquals(d("30000.00"),toman.minAmount());assertEquals(d("32000.00"),toman.maxAmount());
        verify(aggregates).retirePublished(id);verify(aggregates,never()).deleteByMaterialItemId(any());
    }
    @Test void identicalRebuildReusesPublicationIds(){
        var a=quote("1500000","TOMAN","50",2);
        when(observations.findWithin(eq(id),eq(KnowledgeReviewStatus.APPROVED),any(Instant.class),any(Instant.class))).thenReturn(List.of(a));
        var saved=new ArrayList<MaterialPriceAggregateEntity>();
        when(aggregates.saveAll(anyList())).thenAnswer(inv->{List<MaterialPriceAggregateEntity> rows=inv.getArgument(0);saved.addAll(rows);return rows;});
        var first=service.rebuildAggregates(id);
        when(aggregates.findByMaterialItemIdOrderByCalculatedAtDesc(id)).thenReturn(saved);
        var second=service.rebuildAggregates(id);
        assertEquals(first.stream().map(PriceAggregateResponse::id).toList(),second.stream().map(PriceAggregateResponse::id).toList());
        verify(aggregates,times(1)).saveAll(anyList());
    }
    @Test void chronologicalRebuildFlagsBackdatedOrLegacyOutlier(){
        var a=quote("100","IRR","1",5);var b=quote("105","IRR","1",4);var c=quote("95","IRR","1",3);var out=quote("400","IRR","1",2);
        when(observations.findWithin(eq(id),eq(KnowledgeReviewStatus.APPROVED),any(Instant.class),any(Instant.class))).thenReturn(List.of(out,c,a,b));
        var result=service.rebuildAggregates(id);
        assertEquals(3,result.stream().filter(r->r.currencyCode().equals("IRR")).findFirst().orElseThrow().sampleCount());
        var captor=ArgumentCaptor.forClass(MaterialPriceAnomalyEntity.class);verify(anomalies).save(captor.capture());assertEquals(out.getId(),captor.getValue().getObservationId());
    }
    @Test void reviewRequiresReasonAndRevocationIsTerminal(){
        var a=quote("100","IRR","1",2);
        when(observations.findMaterialId(a.getId())).thenReturn(Optional.of(id));when(observations.findById(a.getId())).thenReturn(Optional.of(a));
        assertThrows(IllegalArgumentException.class,()->service.reviewObservation(a.getId(),new ReviewPriceObservationRequest(KnowledgeReviewStatus.REJECTED," "),auth));
        assertEquals(KnowledgeReviewStatus.REJECTED,service.reviewObservation(a.getId(),new ReviewPriceObservationRequest(KnowledgeReviewStatus.REJECTED,"TEST revoke"),auth).reviewStatus());
        assertThrows(IllegalArgumentException.class,()->service.reviewObservation(a.getId(),new ReviewPriceObservationRequest(KnowledgeReviewStatus.APPROVED,"TEST"),auth));
        verify(aggregates).retirePublished(id);
    }
}
