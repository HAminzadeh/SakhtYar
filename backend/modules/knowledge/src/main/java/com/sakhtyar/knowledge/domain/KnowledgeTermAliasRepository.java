package com.sakhtyar.knowledge.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTermAliasRepository extends JpaRepository<KnowledgeTermAliasEntity, UUID> {
    List<KnowledgeTermAliasEntity> findByTermIdOrderByAliasAsc(UUID termId);
    List<KnowledgeTermAliasEntity> findByStatusOrderByAliasNormalizedAsc(KnowledgeReviewStatus status);
}