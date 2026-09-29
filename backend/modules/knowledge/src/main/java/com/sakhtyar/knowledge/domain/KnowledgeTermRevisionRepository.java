package com.sakhtyar.knowledge.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTermRevisionRepository extends JpaRepository<KnowledgeTermRevisionEntity, UUID> {
    List<KnowledgeTermRevisionEntity> findByTermIdOrderByRevisionNoDesc(UUID termId);
    Optional<KnowledgeTermRevisionEntity> findTopByTermIdOrderByRevisionNoDesc(UUID termId);
}