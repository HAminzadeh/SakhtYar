package com.sakhtyar.contract.api;
import com.sakhtyar.contract.domain.ContractRecordEntity;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class ContractDtos {
  private ContractDtos(){}

  public record UpsertContractRequest(
      @NotBlank @Size(max=80) String contractCode,@NotBlank @Size(max=60) String contractType,
      @NotBlank @Size(max=40) String status,@NotBlank @Size(max=300) String title,
      Instant signedAt,LocalDate effectiveFrom,LocalDate effectiveTo,
      @NotBlank @Size(max=20) String schemaVersion,Map<String,Object> terms,Map<String,Object> metadata
  ){}

  public record ContractResponse(
      UUID id,UUID caseId,String contractCode,String contractType,String status,String title,Instant signedAt,
      LocalDate effectiveFrom,LocalDate effectiveTo,String schemaVersion,Map<String,Object> terms,Map<String,Object> metadata,
      String createdBy,Instant createdAt,Instant updatedAt
  ){
    public static ContractResponse from(ContractRecordEntity e){return new ContractResponse(
      e.getId(),e.getCaseId(),e.getContractCode(),e.getContractType(),e.getStatus(),e.getTitle(),e.getSignedAt(),
      e.getEffectiveFrom(),e.getEffectiveTo(),e.getSchemaVersion(),e.getTerms(),e.getMetadata(),
      e.getCreatedBy(),e.getCreatedAt(),e.getUpdatedAt());}
  }

  public record ContractReviewResponse(
      UUID contractId,String contractCode,String status,List<String> presentClauses,
      List<String> missingClauses,double completeness,String disclaimer
  ){}
}
