package com.sakhtyar.contract.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRecordRepository extends JpaRepository<ContractRecordEntity, UUID> {
    List<ContractRecordEntity> findByCaseIdOrderByUpdatedAtDesc(UUID caseId);
}