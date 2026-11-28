package com.sakhtyar.material.domain;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialPriceAnomalyRepository extends JpaRepository<MaterialPriceAnomalyEntity, UUID> {
    @Query(value="select o.material_item_id from public.material_price_anomaly a join public.material_price_observation o on o.id=a.observation_id where a.id=:id",nativeQuery=true)
    Optional<UUID> findMaterialId(@Param("id") UUID id);
    Optional<MaterialPriceAnomalyEntity> findByObservationId(UUID observationId);
    List<MaterialPriceAnomalyEntity> findByStatusOrderByCreatedAtAsc(PriceAnomalyStatus status);
}