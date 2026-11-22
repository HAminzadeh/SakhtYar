package com.sakhtyar.contract.domain;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRecordRepository extends JpaRepository<ContractRecordEntity,UUID> {
    List<ContractRecordEntity> findByCaseIdOrderByUpdatedAtDesc(UUID caseId);
    Optional<ContractRecordEntity> findByCaseIdAndContractCodeIgnoreCase(UUID caseId,String contractCode);

    /** Scalar lookup avoids caching a stale entity before acquiring the parent write lock. */
    @Query("select c.caseId from ContractRecordEntity c where c.id = :id")
    Optional<UUID> findCaseIdById(@Param("id") UUID id);
}
