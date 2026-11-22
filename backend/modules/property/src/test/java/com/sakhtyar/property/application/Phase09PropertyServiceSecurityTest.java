package com.sakhtyar.property.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.casefile.domain.CaseStatus;
import com.sakhtyar.property.api.PropertyDtos.MergePropertyFactsRequest;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class Phase09PropertyServiceSecurityTest {
    private final CaseRepository cases = mock(CaseRepository.class);
    private final CaseAccessService access = mock(CaseAccessService.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PropertyService service = new PropertyService(properties, cases, audit, access);
    private final UUID caseId = UUID.randomUUID();

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private CaseEntity caseRow(String scope, CaseStatus status) {
        CaseEntity row = mock(CaseEntity.class);
        when(row.getDataScope()).thenReturn(scope);
        when(row.getStatus()).thenReturn(status);
        return row;
    }

    @Test void directServiceGetRequiresCaseMembershipOrOwnership() {
        CaseEntity row = caseRow("REAL", CaseStatus.DRAFT);
        when(cases.findById(caseId)).thenReturn(Optional.of(row));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(row);
        assertThrows(ResponseStatusException.class, () -> service.get(caseId));
        verify(access).requireRead(row);
        verifyNoInteractions(properties, audit);
    }

    @Test void directServiceMergeRequiresWriteAuthorization() {
        CaseEntity row = caseRow("REAL", CaseStatus.DRAFT);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(row));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireWrite(row);
        assertThrows(ResponseStatusException.class,
                () -> service.mergeFacts(caseId, new MergePropertyFactsRequest(Map.of("city", "Tehran"))));
        verify(cases).findByIdForUpdate(caseId);
        verify(access).requireWrite(row);
        verifyNoInteractions(properties, audit);
    }

    @Test void archivedCaseRejectsPropertyMutationsBeforePropertyRead() {
        CaseEntity archived = caseRow("REAL", CaseStatus.ARCHIVED);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(archived));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.mergeFacts(caseId, new MergePropertyFactsRequest(Map.of("city", "Tehran"))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(access).requireWrite(archived);
        verifyNoInteractions(properties, audit);
    }

    @Test void testWorkspaceRecordNeverReachesPropertyRepository() {
        CaseEntity test = caseRow("TEST", CaseStatus.DRAFT);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(test));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.mergeFacts(caseId, new MergePropertyFactsRequest(Map.of("city", "Tehran"))));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(access, properties, audit);
    }

    @Test void testerCannotReadRealPropertyEvenWithAdditionalAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("tester", null,
                List.of(new SimpleGrantedAuthority("ROLE_TESTER"), new SimpleGrantedAuthority("PROPERTY_READ"))));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.get(caseId));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(cases, access, properties, audit);
    }

    @Test void authorizedReadDelegatesToCaseAccessAndKeepsResponseShape() {
        CaseEntity row = caseRow("REAL", CaseStatus.DRAFT);
        PropertyEntity entity = mock(PropertyEntity.class);
        when(cases.findById(caseId)).thenReturn(Optional.of(row));
        when(properties.findByCaseId(caseId)).thenReturn(Optional.of(entity));
        assertDoesNotThrow(() -> service.get(caseId));
        verify(access).requireRead(row);
        verify(properties).findByCaseId(caseId);
    }
}
