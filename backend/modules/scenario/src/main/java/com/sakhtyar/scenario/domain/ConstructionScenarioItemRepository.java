package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ConstructionScenarioItemRepository extends JpaRepository<ConstructionScenarioItemEntity,UUID>{List<ConstructionScenarioItemEntity> findByScenarioIdOrderBySortOrderAscIdAsc(UUID scenarioId);}