package com.sakhtyar.knowledge.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTermRepository extends JpaRepository<KnowledgeTermEntity, UUID> {
    Optional<KnowledgeTermEntity> findByCodeIgnoreCase(String code);
    List<KnowledgeTermEntity> findAllByOrderByUpdatedAtDesc();
    List<KnowledgeTermEntity> findByStatusOrderByNameFaAsc(KnowledgeReviewStatus status);
}