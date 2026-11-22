package com.sakhtyar.assembly.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostAssemblyComponentRepository extends JpaRepository<CostAssemblyComponentEntity, UUID> {
    List<CostAssemblyComponentEntity> findByAssemblyIdOrderBySortOrderAscIdAsc(UUID assemblyId);
}