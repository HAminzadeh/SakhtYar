package com.sakhtyar.assembly.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostEstimateLineRepository extends JpaRepository<CostEstimateLineEntity, UUID> {
    List<CostEstimateLineEntity> findByEstimateIdOrderByIdAsc(UUID estimateId);
}