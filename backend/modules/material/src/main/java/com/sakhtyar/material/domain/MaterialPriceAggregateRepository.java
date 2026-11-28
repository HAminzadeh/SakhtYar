package com.sakhtyar.material.domain;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialPriceAggregateRepository extends JpaRepository<MaterialPriceAggregateEntity, UUID> {
    void deleteByMaterialItemId(UUID materialItemId);
    // Old callers, including Assembly, now consume only current and still-eligible references.
    @Query(value="""
        select a.* from public.material_price_aggregate a
        join public.material_item m on m.id=a.material_item_id and m.active
        where a.material_item_id=:itemId and a.current_reference and a.algorithm_version='2.0'
          and a.expires_at>statement_timestamp()
          and exists(select 1 from public.global_currency c where c.id=a.currency_id and c.active)
          and (a.variant_id is null or exists(select 1 from public.material_variant v where v.id=a.variant_id and v.active))
          and jsonb_array_length(a.evidence_ids)=a.sample_count
          and not exists (
            select 1 from jsonb_array_elements_text(a.evidence_ids) e(id)
            left join public.material_price_observation o on o.id=cast(e.id as uuid)
              and o.material_item_id=a.material_item_id and o.data_scope=a.data_scope and o.test_workspace_id is not distinct from a.test_workspace_id
            left join public.knowledge_source s on s.id=o.source_id and s.data_scope=a.data_scope and s.test_workspace_id is not distinct from a.test_workspace_id
            left join public.global_currency c on c.id=o.currency_id
            where o.id is null or s.id is null or not s.enabled or c.id is null or not c.active
              or o.review_status<>'APPROVED' or o.confidence is null or o.confidence<0.50
              or o.observed_at>statement_timestamp() or o.observed_at+interval '720 hours'<=statement_timestamp()
              or exists(select 1 from public.material_price_anomaly n where n.observation_id=o.id and n.status<>'DISMISSED')
          ) order by a.calculated_at desc,a.id
        """,nativeQuery=true)
    List<MaterialPriceAggregateEntity> findByMaterialItemIdOrderByCalculatedAtDesc(@Param("itemId") UUID materialItemId);
    @Query("select a from MaterialPriceAggregateEntity a where a.materialItemId=:id order by a.calculatedAt desc,a.id")
    List<MaterialPriceAggregateEntity> findHistory(@Param("id") UUID id);
    @Modifying(flushAutomatically=true)
    @Query("update MaterialPriceAggregateEntity a set a.currentReference=false where a.materialItemId=:id and a.currentReference=true")
    int retirePublished(@Param("id") UUID id);
}