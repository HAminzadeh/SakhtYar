package com.sakhtyar.regulation.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UrbanEvaluationRepository extends JpaRepository<UrbanEvaluationEntity,UUID>{
    List<UrbanEvaluationEntity> findByPropertyIdOrderByEvaluatedAtDesc(UUID propertyId);
}