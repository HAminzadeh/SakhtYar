package com.sakhtyar.casefile.api;

import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CaseDtos {

    private CaseDtos() {
    }

    public record UpsertCaseRequest(
            @NotBlank String title,
            @NotNull CaseStatus status,
            String description,
            String city,
            String district,
            String address,
            @DecimalMin(value = "0.01", inclusive = true) BigDecimal landAreaM2
    ) {
    }

    public record CaseResponse(
            UUID id,
            String title,
            CaseStatus status,
            String description,
            String city,
            String district,
            String address,
            BigDecimal landAreaM2,
            String createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CaseResponse from(CaseEntity entity) {
            return new CaseResponse(
                    entity.getId(),
                    entity.getTitle(),
                    entity.getStatus(),
                    entity.getDescription(),
                    entity.getCity(),
                    entity.getDistrict(),
                    entity.getAddress(),
                    entity.getLandAreaM2(),
                    entity.getCreatedBy(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}
