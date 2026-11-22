package com.sakhtyar.document.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.casefile.domain.CaseStatus;
import com.sakhtyar.document.domain.DocumentEntity;
import com.sakhtyar.document.domain.DocumentRepository;
import com.sakhtyar.document.infrastructure.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Phase11DocumentServiceTest {
    private DocumentRepository documents;
    private CaseRepository cases;
    private CaseAccessService access;
    private AuditService audit;
    private MinioClient minio;
    private DocumentService service;
    private UUID caseId;
    private CaseEntity currentCase;

    @BeforeEach
    void setup() throws Exception {
        documents = mock(DocumentRepository.class);
        cases = mock(CaseRepository.class);
        access = mock(CaseAccessService.class);
        audit = mock(AuditService.class);
        minio = mock(MinioClient.class);
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        service = new DocumentService(documents, cases, access, audit, minio,
                new StorageProperties("http://localhost:9000", "test", "test", "test-documents"));
        caseId = UUID.randomUUID();
        currentCase = new CaseEntity(caseId, "C-123", "Test", CaseStatus.DRAFT,
                null, null, null, null, null, null, "alice", Instant.now(), Instant.now());
        ReflectionTestUtils.setField(currentCase, "dataScope", "REAL");
        when(cases.findById(caseId)).thenReturn(Optional.of(currentCase));
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(currentCase));
    }

    @AfterEach
    void teardown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void login(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a",
                        AuthorityUtils.createAuthorityList(authorities)));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "safe.pdf", "application/pdf",
                "%PDF-1.7 synthetic".getBytes(StandardCharsets.UTF_8));
    }

    private void beginManagedTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @Test
    void documentReadAuthorityIsMandatoryInsideService() {
        login("CASE_READ");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.list(caseId)).getStatusCode());
        verify(cases, never()).findById(caseId);
    }

    @Test
    void testerCannotReadEvenWithDocumentAuthority() {
        login("DOCUMENT_READ", "ROLE_TESTER");
        assertThrows(ResponseStatusException.class, () -> service.list(caseId));
        verify(cases, never()).findById(caseId);
    }

    @Test
    void listDelegatesActualCaseAccessAndDoesNotLeakOnDenial() {
        login("DOCUMENT_READ");
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(currentCase);
        assertThrows(ResponseStatusException.class, () -> service.list(caseId));
        verify(documents, never()).findByCaseIdOrderByUploadedAtDesc(caseId);
    }

    @Test
    void listAuthorizedUsesCaseScopedQuery() {
        login("DOCUMENT_READ");
        when(documents.findByCaseIdOrderByUploadedAtDesc(caseId)).thenReturn(List.of());
        assertTrue(service.list(caseId).isEmpty());
        verify(access).requireRead(currentCase);
    }

    @Test
    void uploadWithoutDocumentWriteDoesNotTouchStorage() {
        login("CASE_WRITE");
        assertThrows(ResponseStatusException.class, () -> service.upload(caseId, pdf(), "alice"));
        verify(minio, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void documentUploadRequiresCaseWriteAuthorization() {
        login("DOCUMENT_WRITE");
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireWrite(currentCase);
        assertThrows(ResponseStatusException.class, () -> service.upload(caseId, pdf(), "alice"));
        verify(minio, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void spoofedUploaderRejectedBeforeStorageAccess() {
        login("DOCUMENT_WRITE");
        assertThrows(ResponseStatusException.class, () -> service.upload(caseId, pdf(), "bob"));
        verify(minio, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void transactionIsRequiredBeforeExternalWrite() {
        login("DOCUMENT_WRITE");
        assertThrows(IllegalStateException.class, () -> service.upload(caseId, pdf(), "alice"));
        verify(minio, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void rollbackRemovesOnlyFreshlyUploadedObject() throws Exception {
        login("DOCUMENT_WRITE");
        beginManagedTransaction();
        assertNotNull(service.upload(caseId, pdf(), "alice"));
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) {
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(minio).putObject(any(PutObjectArgs.class));
        verify(minio).removeObject(any(RemoveObjectArgs.class));
        verify(audit).record(eq("DOCUMENT"), any(UUID.class), eq("DOCUMENT_UPLOADED"), any());
    }

    @Test
    void committedUploadMustNotDeleteObject() throws Exception {
        login("DOCUMENT_WRITE");
        beginManagedTransaction();
        assertNotNull(service.upload(caseId, pdf(), "alice"));
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) {
            callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(minio, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void failedMetadataSaveCanBeCompensatedAfterRollback() throws Exception {
        login("DOCUMENT_WRITE");
        beginManagedTransaction();
        when(documents.saveAndFlush(any(DocumentEntity.class))).thenThrow(new IllegalStateException("DB failed"));
        assertThrows(IllegalStateException.class, () -> service.upload(caseId, pdf(), "alice"));
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) {
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(minio).removeObject(any(RemoveObjectArgs.class));
        verifyNoInteractions(audit);
    }

    @Test
    void minioPutFailureTriggersBestEffortCleanupOfFreshKey() throws Exception {
        login("DOCUMENT_WRITE");
        beginManagedTransaction();
        when(minio.putObject(any(PutObjectArgs.class)))
                .thenThrow(new java.io.IOException("simulated storage failure"));
        assertThrows(IllegalStateException.class, () -> service.upload(caseId, pdf(), "alice"));
        verify(minio).removeObject(any(RemoveObjectArgs.class));
        verifyNoInteractions(audit);
    }

    @Test
    void downloadRejectsTamperedCrossCaseKeyBeforeMinio() throws Exception {
        login("DOCUMENT_READ");
        UUID documentId = UUID.randomUUID();
        UUID otherCase = UUID.randomUUID();
        DocumentEntity item = new DocumentEntity(documentId, caseId, "safe.pdf", "application/pdf",
                10, "cases/" + otherCase + "/" + documentId + "/safe.pdf", "0".repeat(64),
                "alice", Instant.now());
        when(documents.findById(documentId)).thenReturn(Optional.of(item));
        assertThrows(SecurityException.class, () -> service.download(documentId));
        verify(minio, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    void testScopedCaseCannotUseRealObjectAdapter() {
        login("DOCUMENT_READ", "DOCUMENT_WRITE");
        ReflectionTestUtils.setField(currentCase, "dataScope", "TEST");
        assertThrows(ResponseStatusException.class, () -> service.list(caseId));
        verify(documents, never()).findByCaseIdOrderByUploadedAtDesc(caseId);
    }
}
