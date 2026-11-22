package com.sakhtyar.document.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.application.CaseLifecycle;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.document.api.DocumentDtos.DocumentResponse;
import com.sakhtyar.document.domain.DocumentEntity;
import com.sakhtyar.document.domain.DocumentRepository;
import com.sakhtyar.document.infrastructure.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final CaseRepository caseRepository;
    private final CaseAccessService caseAccess;
    private final AuditService auditService;
    private final MinioClient minio;
    private final StorageProperties storage;

    public DocumentService(
            DocumentRepository repository,
            CaseRepository caseRepository,
            CaseAccessService caseAccess,
            AuditService auditService,
            MinioClient minio,
            StorageProperties storage
    ) {
        this.repository = repository;
        this.caseRepository = caseRepository;
        this.caseAccess = caseAccess;
        this.auditService = auditService;
        this.minio = minio;
        this.storage = storage;
        ensureBucket();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID caseId) {
        requireCase(caseId, false);
        return repository.findByCaseIdOrderByUploadedAtDesc(caseId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional
    public DocumentResponse upload(UUID caseId, MultipartFile file, String uploadedBy) {
        CaseEntity activeCase = requireCase(caseId, true);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (uploadedBy == null || !uploadedBy.equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid document uploader.");
        }
        CaseLifecycle.requireEditable(activeCase.getStatus(), activeCase.getStatus());

        // Do not start external I/O if it cannot be compensated after a database rollback.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("A managed transaction is required for document upload.");
        }
        Phase03DocumentSafety.validateUpload(file);

        UUID documentId = UUID.randomUUID();
        String filename = Phase03DocumentSafety.safeFilename(file.getOriginalFilename());
        String key = "cases/" + caseId + "/" + documentId + "/" + filename;
        Phase03DocumentSafety.requireOwnedObjectKey(caseId, documentId, key);

        boolean rollbackCleanupRegistered = false;
        try {
            String sha256 = sha256(file);
            try (InputStream input = file.getInputStream()) {
                minio.putObject(PutObjectArgs.builder()
                        .bucket(storage.bucket())
                        .object(key)
                        .stream(input, file.getSize(), -1L)
                        .contentType(file.getContentType())
                        .build());
                // Register before closing the stream: even an unexpected close failure
                // must not leave a successfully stored object without compensation.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            removeFreshObject(key);
                        }
                    }
                });
                rollbackCleanupRegistered = true;
            }

            DocumentEntity entity = new DocumentEntity(
                    documentId, caseId, filename, file.getContentType(), file.getSize(),
                    key, sha256, auth.getName(), Instant.now());
            repository.saveAndFlush(entity);
            // Audit participates in the same transaction; never record free-text filenames.
            auditService.record("DOCUMENT", documentId, "DOCUMENT_UPLOADED",
                    Map.of("source", "document", "scope", "REAL"));
            return DocumentResponse.from(entity);
        } catch (Exception e) {
            // putObject may fail after the remote side created an object. This key
            // contains a fresh random UUID, so a defensive delete cannot affect
            // existing documents or objects belonging to another case.
            if (!rollbackCleanupRegistered) {
                removeFreshObject(key);
            }
            throw new IllegalStateException("Document upload failed.", e);
        }
    }

    private void removeFreshObject(String key) {
        try {
            minio.removeObject(RemoveObjectArgs.builder()
                    .bucket(storage.bucket()).object(key).build());
        } catch (Exception cleanupFailure) {
            // Do not log filenames, object keys, credentials or personal metadata.
            log.error("Document orphan cleanup failed; restricted reconciliation required");
        }
    }

    @Transactional(readOnly = true)
    public DownloadedDocument download(UUID documentId) {
        requirePermission("DOCUMENT_READ");
        DocumentEntity entity = repository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        requireCase(entity.getCaseId(), false);
        Phase03DocumentSafety.requireOwnedObjectKey(
                entity.getCaseId(), entity.getId(), entity.getObjectKey());

        try {
            InputStream stream = minio.getObject(GetObjectArgs.builder()
                    .bucket(storage.bucket()).object(entity.getObjectKey()).build());
            return new DownloadedDocument(
                    entity.getOriginalFilename(),
                    entity.getContentType() == null
                            ? MediaType.APPLICATION_OCTET_STREAM
                            : MediaType.parseMediaType(entity.getContentType()),
                    entity.getSizeBytes(), new InputStreamResource(stream));
        } catch (Exception e) {
            throw new IllegalStateException("Document download failed.", e);
        }
    }

    private CaseEntity requireCase(UUID caseId, boolean write) {
        requirePermission(write ? "DOCUMENT_WRITE" : "DOCUMENT_READ");
        CaseEntity entity = (write ? caseRepository.findByIdForUpdate(caseId)
                : caseRepository.findById(caseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
        if (!"REAL".equals(entity.getDataScope())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        }
        if (write) {
            caseAccess.requireWrite(entity);
        } else {
            caseAccess.requireRead(entity);
        }
        return entity;
    }

    private void requirePermission(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || auth.getAuthorities() == null
                || auth.getAuthorities().stream().anyMatch(a -> "ROLE_TESTER".equals(a.getAuthority()))
                || auth.getAuthorities().stream().noneMatch(a -> authority.equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(
                    BucketExistsArgs.builder().bucket(storage.bucket()).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(storage.bucket()).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize MinIO bucket.", e);
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

    public record DownloadedDocument(
            String filename, MediaType contentType, long sizeBytes, InputStreamResource resource) { }
}
