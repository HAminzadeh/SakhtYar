package com.sakhtyar.builder.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBuilderRepository extends JpaRepository<ProjectBuilderEntity, UUID> {
    List<ProjectBuilderEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}