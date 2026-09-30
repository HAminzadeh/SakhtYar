package com.sakhtyar.finance.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialAnalysisRepository extends JpaRepository<FinancialAnalysisEntity,UUID>{
    List<FinancialAnalysisEntity> findByCaseIdOrderByCalculatedAtDesc(UUID caseId);
    List<FinancialAnalysisEntity> findByScenarioIdOrderByCalculatedAtDesc(UUID scenarioId);
}