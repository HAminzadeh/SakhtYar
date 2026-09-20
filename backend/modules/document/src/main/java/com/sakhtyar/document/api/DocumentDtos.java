package com.sakhtyar.document.api;

import com.sakhtyar.document.domain.DocumentEntity;
import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentResponse(
            UUID id,
            UUID caseId,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256,
            String uploadedBy,
            Instant uploadedAt
    ) {
        public static DocumentResponse from(DocumentEntity entity) {
            return new DocumentResponse(
                    entity.getId(),
                    entity.getCaseId(),
                    entity.getOriginalFilename(),
                    entity.getContentType(),
                    entity.getSizeBytes(),
                    entity.getSha256(),
                    entity.getUploadedBy(),
                    entity.getUploadedAt()
            );
        }
    }
}
