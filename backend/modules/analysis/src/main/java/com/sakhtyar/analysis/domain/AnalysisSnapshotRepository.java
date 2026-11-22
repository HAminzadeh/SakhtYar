package com.sakhtyar.analysis.domain;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisSnapshotRepository extends JpaRepository<AnalysisSnapshotEntity,UUID> {
    List<AnalysisSnapshotEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
    List<AnalysisSnapshotEntity> findByCaseIdAndAnalysisTypeOrderByCreatedAtDesc(UUID caseId,String analysisType);
    List<AnalysisSnapshotEntity> findByScenarioIdOrderByCreatedAtDesc(UUID scenarioId);
}