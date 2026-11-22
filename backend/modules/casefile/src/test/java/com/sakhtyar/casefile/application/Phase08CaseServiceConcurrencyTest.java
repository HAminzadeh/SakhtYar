package com.sakhtyar.casefile.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.api.CaseDtos.UpsertCaseRequest;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.casefile.domain.CaseStatus;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class Phase08CaseServiceConcurrencyTest {
    private final CaseRepository repository = mock(CaseRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final CaseAccessService access = mock(CaseAccessService.class);
    private final CaseService service = new CaseService(repository, audit, access);
    private final UUID id = UUID.randomUUID();
    private static final Instant LAST = Instant.parse("2026-11-22T07:30:00.123456Z");

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void creator() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("creator", null,
                        List.of(new SimpleGrantedAuthority("CASE_WRITE"),
                                new SimpleGrantedAuthority("CASE_READ"))));
    }

    private UpsertCaseRequest request() {
        return new UpsertCaseRequest("Case 123", CaseStatus.NEGOTIATION,
                "desc", null, null, null, null, null);
    }

    private CaseEntity row(CaseStatus status) {
        CaseEntity entity = mock(CaseEntity.class);
        when(entity.getStatus()).thenReturn(status);
        when(entity.getUpdatedAt()).thenReturn(LAST);
        when(entity.getDataScope()).thenReturn("REAL");
        when(entity.getTitle()).thenReturn("Case 123");
        when(entity.getId()).thenReturn(id);
        return entity;
    }

    @Test void updateUsesPessimisticWriteLockAndAuditsSuccessfulMutation() throws Exception {
        creator();
        CaseEntity entity = row(CaseStatus.DRAFT);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        assertNotNull(service.update(id, request(), CaseRevision.etag(LAST)));
        verify(repository).findByIdForUpdate(id);
        verify(access).requireRead(entity);
        verify(access).requireWrite(entity);
        verify(entity).update(eq("Case 123"), eq(CaseStatus.NEGOTIATION),
                eq("desc"), isNull(), isNull(), isNull(), isNull(), isNull());
        verify(audit).record(eq("CASE"), eq(id), eq("CASE_UPDATED"), anyMap());
        Method method = CaseRepository.class.getMethod("findByIdForUpdate", UUID.class);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
    }

    @Test void staleUpdateHasNoMutationAndNoAudit() {
        creator(); CaseEntity entity = row(CaseStatus.DRAFT);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.update(id, request(), CaseRevision.etag(LAST.minusSeconds(10))));
        assertEquals(HttpStatus.PRECONDITION_FAILED, error.getStatusCode());
        verify(entity, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test void archivedUpdateIsRejectedBeforeMutationAndAudit() {
        creator(); CaseEntity entity = row(CaseStatus.ARCHIVED);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.update(id, request(), null));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(audit);
    }

    @Test void testerIsRejectedBeforeRepositoryAccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", null,
                        List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
        assertThrows(ResponseStatusException.class, () -> service.update(id, request(), null));
        verifyNoInteractions(repository, audit, access);
    }
}
