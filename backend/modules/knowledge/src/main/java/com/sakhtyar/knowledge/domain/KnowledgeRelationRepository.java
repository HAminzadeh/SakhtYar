package com.sakhtyar.knowledge.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeRelationRepository extends JpaRepository<KnowledgeRelationEntity, UUID> {
    List<KnowledgeRelationEntity> findByFromTermIdOrToTermId(UUID fromTermId, UUID toTermId);
}