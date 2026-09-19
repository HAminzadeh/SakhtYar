package com.sakhtyar.document.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    List<DocumentEntity> findByCaseIdOrderByUploadedAtDesc(UUID caseId);
}
