package com.sakhtyar.sensitivity.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface SensitivityAnalysisRepository extends JpaRepository<SensitivityAnalysisEntity,UUID>{
    List<SensitivityAnalysisEntity> findByFinancialAnalysisIdOrderByCalculatedAtDesc(UUID financialAnalysisId);
    List<SensitivityAnalysisEntity> findByCaseIdOrderByCalculatedAtDesc(UUID caseId);
}