package com.sakhtyar.assembly.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostEstimateRepository extends JpaRepository<CostEstimateEntity, UUID> {
    List<CostEstimateEntity> findByAssemblyIdOrderByCalculatedAtDesc(UUID assemblyId);
    java.util.Optional<CostEstimateEntity> findByAssemblyIdAndCalculatedByAndRequestKey(
            UUID assemblyId, String calculatedBy, UUID requestKey);
}