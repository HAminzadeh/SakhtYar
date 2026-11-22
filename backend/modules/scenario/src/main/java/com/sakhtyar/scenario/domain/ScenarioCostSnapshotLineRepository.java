package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ScenarioCostSnapshotLineRepository extends JpaRepository<ScenarioCostSnapshotLineEntity,UUID>{List<ScenarioCostSnapshotLineEntity> findBySnapshotIdOrderByIdAsc(UUID snapshotId);}