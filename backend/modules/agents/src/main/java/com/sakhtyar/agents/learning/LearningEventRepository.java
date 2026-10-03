package com.sakhtyar.agents.learning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningEventRepository
        extends JpaRepository<LearningEventEntity,UUID> {

    List<LearningEventEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    List<LearningEventEntity> findByInputGatewayRequestIdOrderByCreatedAtDesc(
            UUID inputGatewayRequestId
    );
}