package com.sakhtyar.regulation.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UrbanEvaluationResultRepository extends JpaRepository<UrbanEvaluationResultEntity,UUID>{
    List<UrbanEvaluationResultEntity> findByEvaluationIdOrderByIdAsc(UUID evaluationId);
}