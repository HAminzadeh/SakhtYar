package com.sakhtyar.agents.input;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersianInputRequestRepository
        extends JpaRepository<PersianInputRequestEntity,UUID> {

    List<PersianInputRequestEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}