package com.sakhtyar.material.domain;

import com.sakhtyar.knowledge.domain.KnowledgeReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialPriceObservationRepository extends JpaRepository<MaterialPriceObservationEntity, UUID> {
    @Query("select o.materialItemId from MaterialPriceObservationEntity o where o.id=:id")
    Optional<UUID> findMaterialId(@Param("id") UUID id);
    @Query(value="select exists(select 1 from public.global_currency where upper(code)=upper(:code) and active)", nativeQuery=true)
    boolean activeCurrency(@Param("code") String code);
    @Query(value="""
        select exists(select 1 from public.knowledge_source s join public.material_item m
          on m.id=:itemId and m.data_scope=s.data_scope and m.test_workspace_id is not distinct from s.test_workspace_id
          where s.id=:sourceId and s.enabled)
        """,nativeQuery=true)
    boolean sourceMatchesMaterial(@Param("sourceId") UUID sourceId,@Param("itemId") UUID itemId);
    @Query(value="""
        select s.id as id,s.source_code as code,s.name as name,s.trust_level as trust from public.knowledge_source s join public.material_item m on m.id=:itemId
          and m.data_scope=s.data_scope and m.test_workspace_id is not distinct from s.test_workspace_id
          where s.enabled order by s.name,s.id
        """,nativeQuery=true)
    List<PriceSourceView> eligibleSources(@Param("itemId") UUID itemId);
    interface PriceSourceView { UUID getId(); String getCode(); String getName(); String getTrust(); }
    Optional<MaterialPriceObservationEntity> findByMaterialItemIdAndQuoteKey(UUID itemId,String quoteKey);
    @Query("select o from MaterialPriceObservationEntity o where o.materialItemId=:itemId and o.sourceId=:sourceId and o.observedAt=:observed and o.quoteKey is null")
    List<MaterialPriceObservationEntity> findLegacyCandidates(@Param("itemId") UUID itemId,@Param("sourceId") UUID sourceId,@Param("observed") java.time.Instant observed);
    @Query("select o from MaterialPriceObservationEntity o where o.materialItemId=:itemId and o.reviewStatus=:status and o.currencyId is not null and o.observedAt>:start and o.observedAt<=:end order by o.observedAt,o.id")
    List<MaterialPriceObservationEntity> findWithin(@Param("itemId") UUID itemId,@Param("status") KnowledgeReviewStatus status,@Param("start") java.time.Instant start,@Param("end") java.time.Instant end);
    List<MaterialPriceObservationEntity> findByMaterialItemIdOrderByObservedAtDesc(UUID materialItemId);
    List<MaterialPriceObservationEntity> findByMaterialItemIdAndReviewStatusOrderByObservedAtAsc(
            UUID materialItemId, KnowledgeReviewStatus reviewStatus
    );
}