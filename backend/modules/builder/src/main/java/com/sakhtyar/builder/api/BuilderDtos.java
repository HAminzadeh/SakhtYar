package com.sakhtyar.builder.api;

import com.sakhtyar.builder.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class BuilderDtos {
  private BuilderDtos(){}

  public record UpsertBuilderRequest(
      @NotBlank @Size(max=250) String legalName,@Size(max=250) String tradeName,
      @Size(max=30) String nationalIdentifier,@Size(max=60) String registrationNo,
      @Size(max=30) String mobile,@Size(max=30) String phone,@Email @Size(max=250) String email,
      @Size(max=500) String website,@Size(max=100) String province,@Size(max=100) String city,
      @Size(max=4000) String address,boolean verified,Map<String,Object> metadata
  ){}

  public record BuilderResponse(
      UUID id,String legalName,String tradeName,String nationalIdentifier,String registrationNo,String mobile,
      String phone,String email,String website,String province,String city,String address,boolean verified,
      Map<String,Object> metadata,Instant createdAt,Instant updatedAt
  ){
    public static BuilderResponse from(BuilderProfileEntity e){return new BuilderResponse(
      e.getId(),e.getLegalName(),e.getTradeName(),e.getNationalIdentifier(),e.getRegistrationNo(),e.getMobile(),
      e.getPhone(),e.getEmail(),e.getWebsite(),e.getProvince(),e.getCity(),e.getAddress(),e.isVerified(),
      e.getMetadata(),e.getCreatedAt(),e.getUpdatedAt());}
  }

  public record LinkBuilderRequest(
      @NotNull UUID builderId,@NotBlank @Size(max=50) String role,@NotBlank @Size(max=40) String status,
      boolean primary,@DecimalMin("0") @DecimalMax("100") BigDecimal proposedSharePercent,Map<String,Object> metadata
  ){}

  public record ProjectBuilderResponse(
      UUID id,UUID caseId,UUID builderId,String role,String status,boolean primary,BigDecimal proposedSharePercent,
      Map<String,Object> metadata,Instant createdAt,Instant updatedAt
  ){
    public static ProjectBuilderResponse from(ProjectBuilderEntity e){return new ProjectBuilderResponse(
      e.getId(),e.getCaseId(),e.getBuilderId(),e.getRole(),e.getStatus(),e.isPrimary(),e.getProposedSharePercent(),
      e.getMetadata(),e.getCreatedAt(),e.getUpdatedAt());}
  }

  public record MatchRequest(@DecimalMin("0") BigDecimal requiredCapital,@Min(1) @Max(100) Integer limit){}
  public record MatchItem(UUID builderId,String legalName,String tradeName,int score,List<String> reasons,Map<String,Object> evidence){}
  public record MatchResponse(UUID caseId,String district,BigDecimal projectAreaM2,BigDecimal requiredCapital,int candidateCount,List<MatchItem> matches){}
}
