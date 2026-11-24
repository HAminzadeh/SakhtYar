package com.sakhtyar.builder.api;

import com.sakhtyar.builder.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class BuilderDtos {
    private BuilderDtos() { }
    public record UpsertBuilderRequest(
            @NotBlank @Size(max=250) String legalName,@Size(max=250) String tradeName,
            @Size(max=30) String nationalIdentifier,@Size(max=60) String registrationNo,
            @Size(max=30) String mobile,@Size(max=30) String phone,@Email @Size(max=250) String email,
            @Size(max=500) String website,@Size(max=100) String province,@Size(max=100) String city,
            @Size(max=4000) String address,boolean verified,Map<String,Object> metadata) { }
    public record BuilderResponse(UUID id,String legalName,String tradeName,String nationalIdentifier,String registrationNo,
            String mobile,String phone,String email,String website,String province,String city,String address,
            boolean verified,Map<String,Object> metadata,Instant createdAt,Instant updatedAt) {
        public static BuilderResponse from(BuilderProfileEntity e){return from(e,true);}
        public static BuilderResponse from(BuilderProfileEntity e,boolean privileged){return new BuilderResponse(
            e.getId(),e.getLegalName(),e.getTradeName(),privileged?e.getNationalIdentifier():null,
            privileged?e.getRegistrationNo():null,privileged?e.getMobile():null,privileged?e.getPhone():null,
            privileged?e.getEmail():null,privileged?e.getWebsite():null,e.getProvince(),e.getCity(),
            privileged?e.getAddress():null,e.isVerified(),privileged?e.getMetadata():Map.of(),e.getCreatedAt(),e.getUpdatedAt());}
    }
    public record BuilderPageResponse(List<BuilderResponse> items,long total,int page,int size) { }
    public record LinkBuilderRequest(@NotNull UUID builderId,@NotBlank @Size(max=50) String role,
            @NotBlank @Size(max=40) String status,boolean primary,
            @DecimalMin("0") @DecimalMax("100") BigDecimal proposedSharePercent,Map<String,Object> metadata) { }
    public record ProjectBuilderResponse(UUID id,UUID caseId,UUID builderId,String role,String status,
            boolean primary,BigDecimal proposedSharePercent,Map<String,Object> metadata,
            Instant createdAt,Instant updatedAt) {
        public static ProjectBuilderResponse from(ProjectBuilderEntity e){return new ProjectBuilderResponse(
            e.getId(),e.getCaseId(),e.getBuilderId(),e.getRole(),e.getStatus(),e.isPrimary(),
            e.getProposedSharePercent(),e.getMetadata(),e.getCreatedAt(),e.getUpdatedAt());}
    }
    public record MatchRequest(@DecimalMin("0") BigDecimal requiredCapital,@Min(1) @Max(100) Integer limit) { }
    public record MatchItem(UUID builderId,String legalName,String tradeName,int score,List<String> reasons,Map<String,Object> evidence) { }
    public record MatchResponse(UUID caseId,String district,BigDecimal projectAreaM2,BigDecimal requiredCapital,int candidateCount,List<MatchItem> matches) { }

    public record AccountBindingRequest(@NotNull UUID builderId,@NotBlank @Size(max=100) String username,
            @NotBlank @Size(max=500) String reviewReference) { }
    public record VerifyBuilderRequest(@NotNull UUID builderId,@NotNull Boolean approved,
            @NotBlank @Size(max=500) String evidenceReference) { }
    public record InvitationRequest(@NotNull UUID builderId,@Min(1) @Max(90) int validDays) { }
    public record InvitationResponse(UUID id,UUID caseId,UUID builderId,String status,Instant expiresAt,
            Instant createdAt,Instant updatedAt) {
        public static InvitationResponse from(BuilderInvitationEntity e){return new InvitationResponse(
                e.getId(),e.getCaseId(),e.getBuilderId(),e.getStatus(),e.getExpiresAt(),e.getCreatedAt(),e.getUpdatedAt());}
    }
    public record SubmitProposalRequest(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal proposedSharePercent,
            @NotNull @Min(1) @Max(240) Integer durationMonths,Map<String,Object> terms) { }
    public record ProposalResponse(UUID id,UUID caseId,UUID builderId,UUID invitationId,String status,
            int revisionNo,BigDecimal proposedSharePercent,int durationMonths,Map<String,Object> terms,
            Instant createdAt,Instant updatedAt) {
        public static ProposalResponse from(BuilderProposalEntity p){return new ProposalResponse(p.getId(),
            p.getCaseId(),p.getBuilderId(),p.getInvitationId(),p.getStatus(),p.getRevisionNo(),
            p.getProposedSharePercent(),p.getDurationMonths(),p.getTerms(),p.getCreatedAt(),p.getUpdatedAt());}
    }
    public record ProposalVersionResponse(UUID id,UUID proposalId,int revisionNo,Map<String,Object> snapshot,
            String recordedBy,Instant recordedAt) {
        public static ProposalVersionResponse from(BuilderProposalRevisionEntity v){return new ProposalVersionResponse(
            v.getId(),v.getProposalId(),v.getRevisionNo(),v.getSnapshot(),v.getRecordedBy(),v.getRecordedAt());}
    }
    public record ProposalDecisionRequest(@NotBlank String action) { }
}
