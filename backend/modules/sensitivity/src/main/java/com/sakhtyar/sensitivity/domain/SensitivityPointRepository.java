package com.sakhtyar.sensitivity.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface SensitivityPointRepository extends JpaRepository<SensitivityPointEntity,UUID>{
    List<SensitivityPointEntity> findBySensitivityAnalysisIdOrderBySequenceNoAsc(UUID sensitivityAnalysisId);
}