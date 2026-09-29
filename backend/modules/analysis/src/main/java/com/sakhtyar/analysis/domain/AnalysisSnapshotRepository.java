package com.sakhtyar.analysis.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisSnapshotRepository extends JpaRepository<AnalysisSnapshotEntity, UUID> {
    List<AnalysisSnapshotEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}