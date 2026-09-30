package com.sakhtyar.analysis.feasibility.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FeasibilityAssessmentRepository extends JpaRepository<FeasibilityAssessmentEntity,UUID>{
    List<FeasibilityAssessmentEntity> findByCaseIdOrderByAssessedAtDesc(UUID caseId);
    List<FeasibilityAssessmentEntity> findByScenarioIdOrderByAssessedAtDesc(UUID scenarioId);
}