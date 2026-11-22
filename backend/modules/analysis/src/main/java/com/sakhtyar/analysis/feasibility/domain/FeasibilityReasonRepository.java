package com.sakhtyar.analysis.feasibility.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FeasibilityReasonRepository extends JpaRepository<FeasibilityReasonEntity,UUID>{
    List<FeasibilityReasonEntity> findByAssessmentIdOrderByIdAsc(UUID assessmentId);
}