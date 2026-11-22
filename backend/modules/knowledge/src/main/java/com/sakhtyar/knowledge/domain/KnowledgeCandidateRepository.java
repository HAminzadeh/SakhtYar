package com.sakhtyar.knowledge.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeCandidateRepository extends JpaRepository<KnowledgeCandidateEntity, UUID> {
    List<KnowledgeCandidateEntity> findAllByOrderByCreatedAtDesc();
    List<KnowledgeCandidateEntity> findByStatusOrderByCreatedAtAsc(KnowledgeReviewStatus status);
}