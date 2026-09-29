package com.sakhtyar.material.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialPriceAnomalyRepository extends JpaRepository<MaterialPriceAnomalyEntity, UUID> {
    Optional<MaterialPriceAnomalyEntity> findByObservationId(UUID observationId);
    List<MaterialPriceAnomalyEntity> findByStatusOrderByCreatedAtAsc(PriceAnomalyStatus status);
}