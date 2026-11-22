package com.sakhtyar.knowledge.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSourceEntity, UUID> {
    Optional<KnowledgeSourceEntity> findBySourceCode(String sourceCode);
}