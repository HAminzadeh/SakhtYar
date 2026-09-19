package com.sakhtyar.document.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.document.api.DocumentDtos.DocumentResponse;
import com.sakhtyar.document.domain.DocumentEntity;
import com.sakhtyar.document.domain.DocumentRepository;
import com.sakhtyar.shared.config.StorageProperties;
import io.minio.*;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final CaseRepository caseRepository;
    private final AuditService auditService;
    private final MinioClient minio;
    private final StorageProperties storage;

    public DocumentService(
            DocumentRepository repository,
            CaseRepository caseRepository,
            AuditService auditService,
            MinioClient minio,
            StorageProperties storage
    ) {
        this.repository = repository;
        this.caseRepository = caseRepository;
        this.auditService = auditService;
        this.minio = minio;
        this.storage = storage;
        ensureBucket();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID caseId) {
        requireCase(caseId);
        return repository.findByCaseIdOrderByUploadedAtDesc(caseId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional
    public DocumentResponse upload(
            UUID caseId,
            MultipartFile file,
            String uploadedBy
    ) {
        requireCase(caseId);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty.");
        }

        UUID documentId = UUID.randomUUID();
        String filename = sanitizeFilename(file.getOriginalFilename());
        String key = "cases/" + caseId + "/" + documentId + "/" + filename;

        try {
            String sha256 = sha256(file);

            try (InputStream input = file.getInputStream()) {
                minio.putObject(
                        PutObjectArgs.builder()
                                .bucket(storage.bucket())
                                .object(key)
                                .stream(input, file.getSize(), -1L)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            DocumentEntity entity = new DocumentEntity(
                    documentId,
                    caseId,
                    filename,
                    file.getContentType(),
                    file.getSize(),
                    key,
                    sha256,
                    uploadedBy,
                    Instant.now()
            );

            repository.save(entity);

            auditService.record(
                    "DOCUMENT",
                    documentId,
                    "DOCUMENT_UPLOADED",
                    Map.of(
                            "caseId", caseId.toString(),
                            "filename", filename,
                            "sizeBytes", file.getSize()
                    )
            );

            return DocumentResponse.from(entity);
        } catch (Exception e) {
            throw new IllegalStateException("Document upload failed.", e);
        }
    }

    @Transactional(readOnly = true)
    public DownloadedDocument download(UUID documentId) {
        DocumentEntity entity = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found."));

        try {
            InputStream stream = minio.getObject(
                    GetObjectArgs.builder()
                            .bucket(storage.bucket())
                            .object(entity.getObjectKey())
                            .build()
            );

            return new DownloadedDocument(
                    entity.getOriginalFilename(),
                    entity.getContentType() == null
                            ? MediaType.APPLICATION_OCTET_STREAM
                            : MediaType.parseMediaType(entity.getContentType()),
                    entity.getSizeBytes(),
                    new InputStreamResource(stream)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Document download failed.", e);
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(storage.bucket())
                            .build()
            );

            if (!exists) {
                minio.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(storage.bucket())
                                .build()
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize MinIO bucket.", e);
        }
    }

    private void requireCase(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new IllegalArgumentException("Case not found.");
        }
    }

    private String sha256(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sanitizeFilename(String original) {
        String fallback = "document.bin";
        if (original == null || original.isBlank()) {
            return fallback;
        }

        String normalized = original
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\0", "");

        return normalized.length() > 240
                ? normalized.substring(normalized.length() - 240)
                : normalized;
    }

    public record DownloadedDocument(
            String filename,
            MediaType contentType,
            long sizeBytes,
            InputStreamResource resource
    ) {
    }
}

