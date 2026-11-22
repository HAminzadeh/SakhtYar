package com.sakhtyar.finance.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialParticipationAllocationRepository extends JpaRepository<FinancialParticipationAllocationEntity,UUID>{
    List<FinancialParticipationAllocationEntity> findByFinancialAnalysisIdOrderByIdAsc(UUID financialAnalysisId);
}