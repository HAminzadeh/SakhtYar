package com.sakhtyar.contract.domain;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRevisionRepository extends JpaRepository<ContractRevisionEntity,UUID> {
    Optional<ContractRevisionEntity> findTopByContractIdOrderByRevisionNoDesc(UUID contractId);
    List<ContractRevisionEntity> findByContractIdOrderByRevisionNoAsc(UUID contractId);
    Optional<ContractRevisionEntity> findByContractIdAndRevisionNo(UUID contractId,int revisionNo);
}
