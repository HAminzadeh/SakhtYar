package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeDtos {

    private KnowledgeDtos() {}

    public record UpsertTermRequest(
            @NotBlank @Size(max = 120) String code,
            @NotBlank @Size(max = 80) String domain,
            @Size(max = 120) String category,
            @NotBlank @Size(max = 300) String nameFa,
            @Size(max = 300) String nameEn,
            String definition,
            @Size(max = 40) String unitCode,
            @NotNull KnowledgeReviewStatus status,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            UUID sourceId,
            @Size(max = 3000) String provenanceUrl,
            Map<String, Object> metadata,
            @Size(max = 500) String changeReason
    ) {}

    public record CreateAliasRequest(
            @NotBlank @Size(max = 10) String locale,
            @NotBlank @Size(max = 300) String alias,
            @NotNull KnowledgeAliasType aliasType,
            KnowledgeReviewStatus status,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            UUID sourceId
    ) {}

    public record CreateRelationRequest(
            @NotNull UUID fromTermId,
            @NotNull KnowledgeRelationType relationType,
            @NotNull UUID toTermId,
            KnowledgeReviewStatus status,
            UUID sourceId,
            Map<String, Object> metadata
    ) {}

    public record CreateCandidateRequest(
            @NotNull KnowledgeCandidateType candidateType,
            @NotNull KnowledgeCandidateOrigin origin,
            @NotBlank String rawInput,
            Map<String, Object> normalizedPayload,
            @Size(max = 120) String proposedTermCode,
            @Size(max = 300) String proposedNameFa,
            @Size(max = 300) String proposedNameEn,
            UUID sourceId,
            @Size(max = 3000) String sourceUrl,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            String reason
    ) {}

    public record ReviewCandidateRequest(
            @NotNull KnowledgeReviewStatus status,
            String note
    ) {}

    public record TermResponse(
            UUID id, String code, String domain, String category, String nameFa, String nameEn,
            String definition, String unitCode, KnowledgeReviewStatus status, BigDecimal confidence,
            UUID sourceId, String provenanceUrl, Map<String, Object> metadata,
            String createdBy, String updatedBy, Instant createdAt, Instant updatedAt
    ) {
        public static TermResponse from(KnowledgeTermEntity e) {
            return new TermResponse(
                    e.getId(), e.getCode(), e.getDomain(), e.getCategory(), e.getNameFa(), e.getNameEn(),
                    e.getDefinition(), e.getUnitCode(), e.getStatus(), e.getConfidence(),
                    e.getSourceId(), e.getProvenanceUrl(), e.getMetadata(),
                    e.getCreatedBy(), e.getUpdatedBy(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record AliasResponse(
            UUID id, UUID termId, String locale, String alias, String aliasNormalized,
            KnowledgeAliasType aliasType, KnowledgeReviewStatus status, BigDecimal confidence,
            UUID sourceId, String createdBy, Instant createdAt
    ) {
        public static AliasResponse from(KnowledgeTermAliasEntity e) {
            return new AliasResponse(
                    e.getId(), e.getTermId(), e.getLocale(), e.getAlias(), e.getAliasNormalized(),
                    e.getAliasType(), e.getStatus(), e.getConfidence(), e.getSourceId(),
                    e.getCreatedBy(), e.getCreatedAt()
            );
        }
    }

    public record RelationResponse(
            UUID id, UUID fromTermId, KnowledgeRelationType relationType, UUID toTermId,
            KnowledgeReviewStatus status, UUID sourceId, Map<String, Object> metadata,
            String createdBy, Instant createdAt
    ) {
        public static RelationResponse from(KnowledgeRelationEntity e) {
            return new RelationResponse(
                    e.getId(), e.getFromTermId(), e.getRelationType(), e.getToTermId(),
                    e.getStatus(), e.getSourceId(), e.getMetadata(), e.getCreatedBy(), e.getCreatedAt()
            );
        }
    }

    public record CandidateResponse(
            UUID id, KnowledgeCandidateType candidateType, KnowledgeCandidateOrigin origin,
            String rawInput, Map<String, Object> normalizedPayload,
            String proposedTermCode, String proposedNameFa, String proposedNameEn,
            UUID sourceId, String sourceUrl, KnowledgeReviewStatus status, BigDecimal confidence,
            String reason, String reviewNote, String createdBy, String reviewedBy,
            Instant reviewedAt, Instant createdAt, Instant updatedAt
    ) {
        public static CandidateResponse from(KnowledgeCandidateEntity e) {
            return new CandidateResponse(
                    e.getId(), e.getCandidateType(), e.getOrigin(), e.getRawInput(), e.getNormalizedPayload(),
                    e.getProposedTermCode(), e.getProposedNameFa(), e.getProposedNameEn(),
                    e.getSourceId(), e.getSourceUrl(), e.getStatus(), e.getConfidence(),
                    e.getReason(), e.getReviewNote(), e.getCreatedBy(), e.getReviewedBy(),
                    e.getReviewedAt(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record RevisionResponse(
            UUID id, UUID termId, int revisionNo, Map<String, Object> snapshot,
            String changeReason, String changedBy, Instant createdAt
    ) {
        public static RevisionResponse from(KnowledgeTermRevisionEntity e) {
            return new RevisionResponse(
                    e.getId(), e.getTermId(), e.getRevisionNo(), e.getSnapshot(),
                    e.getChangeReason(), e.getChangedBy(), e.getCreatedAt()
            );
        }
    }
}