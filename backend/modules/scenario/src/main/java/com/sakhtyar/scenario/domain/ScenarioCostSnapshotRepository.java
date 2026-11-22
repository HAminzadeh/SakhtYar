package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ScenarioCostSnapshotRepository extends JpaRepository<ScenarioCostSnapshotEntity,UUID>{List<ScenarioCostSnapshotEntity> findByScenarioIdOrderByCalculatedAtDesc(UUID scenarioId);}