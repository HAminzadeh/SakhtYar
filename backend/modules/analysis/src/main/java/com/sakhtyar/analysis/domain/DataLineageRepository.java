package com.sakhtyar.analysis.domain;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataLineageRepository extends JpaRepository<DataLineageEntity,UUID> {
    List<DataLineageEntity> findByAnalysisSnapshotIdOrderByCreatedAtAsc(UUID analysisSnapshotId);
    List<DataLineageEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}