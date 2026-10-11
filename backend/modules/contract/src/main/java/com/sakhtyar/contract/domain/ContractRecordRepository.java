package com.sakhtyar.contract.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ContractRecordRepository extends JpaRepository<ContractRecordEntity,UUID>{
  List<ContractRecordEntity> findByCaseIdOrderByUpdatedAtDesc(UUID caseId);
  Optional<ContractRecordEntity> findByCaseIdAndContractCodeIgnoreCase(UUID caseId,String contractCode);
}
