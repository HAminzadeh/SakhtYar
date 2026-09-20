package com.sakhtyar.casefile.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {
    List<CaseEntity> findAllByOrderByUpdatedAtDesc();
}
