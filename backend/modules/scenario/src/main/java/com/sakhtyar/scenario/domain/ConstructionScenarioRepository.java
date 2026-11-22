package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ConstructionScenarioRepository extends JpaRepository<ConstructionScenarioEntity,UUID>{Optional<ConstructionScenarioEntity> findByCodeIgnoreCase(String code);List<ConstructionScenarioEntity> findAllByOrderByUpdatedAtDesc();}