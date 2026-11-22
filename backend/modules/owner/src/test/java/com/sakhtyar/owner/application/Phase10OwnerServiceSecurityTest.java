package com.sakhtyar.owner.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.casefile.domain.CaseStatus;
import com.sakhtyar.owner.api.OwnerDtos.UpsertOwnerRequest;
import com.sakhtyar.owner.domain.OwnerEntity;
import com.sakhtyar.owner.domain.OwnerRepository;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase10OwnerServiceSecurityTest {
    private final UUID caseId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final CaseRepository cases = mock(CaseRepository.class);
    private final CaseAccessService access = mock(CaseAccessService.class);
    private final AuditService audit = mock(AuditService.class);
    private final OwnerService service = new OwnerService(owners, properties, audit, cases, access);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void auth(String name, String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, null,
                        java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
    private CaseEntity caseRow(String scope, CaseStatus status, boolean lock) {
        CaseEntity c = mock(CaseEntity.class);
        when(c.getDataScope()).thenReturn(scope);
        if ("REAL".equals(scope)) when(c.getStatus()).thenReturn(status);
        if (lock) when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(c));
        else when(cases.findById(caseId)).thenReturn(Optional.of(c));
        return c;
    }
    private PropertyEntity property(String scope) {
        PropertyEntity p = mock(PropertyEntity.class);
        when(p.getDataScope()).thenReturn(scope);
        when(p.getCaseId()).thenReturn(caseId);
        when(p.getId()).thenReturn(propertyId);
        when(properties.findByCaseId(caseId)).thenReturn(Optional.of(p));
        return p;
    }
    private UpsertOwnerRequest request(int n, int d) {
        return new UpsertOwnerRequest("Ali", "Karimi", null, null, n, d, false);
    }
    @Test void readOnlyMemberCannotWriteEvenByCallingServiceDirectly() {
        auth("member", "OWNER_READ");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.create(caseId, request(1, 2))).getStatusCode());
        verifyNoInteractions(owners, properties, cases);
    }
    @Test void testerWithWritePermissionIsDenied() {
        auth("tester", "ROLE_TESTER", "OWNER_WRITE");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.create(caseId, request(1, 2))).getStatusCode());
        verifyNoInteractions(owners, properties, cases);
    }
    @Test void crossScopePropertyIsNotVisibleToWriter() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        caseRow("REAL", CaseStatus.DRAFT, true);
        property("TEST");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.create(caseId, request(1, 2))).getStatusCode());
        verifyNoInteractions(owners);
    }
    @Test void archivedCaseCannotHaveOwnerMutations() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        caseRow("REAL", CaseStatus.ARCHIVED, true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.create(caseId, request(1, 2))).getStatusCode());
        verifyNoInteractions(properties, owners);
    }
    @Test void nonCreatorDenialStopsBeforeReadingPersonalData() {
        auth("stranger", "OWNER_READ");
        CaseEntity c = caseRow("REAL", CaseStatus.DRAFT, false);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(c);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.list(caseId)).getStatusCode());
        verifyNoInteractions(properties, owners);
    }
    @Test void createsUnderParentRowLockAndPersistsBeforeAuditing() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        CaseEntity c = caseRow("REAL", CaseStatus.DRAFT, true);
        property("REAL");
        when(owners.findByPropertyIdOrderByCreatedAtAsc(propertyId)).thenReturn(List.of());
        service.create(caseId, request(1, 2));
        verify(cases).findByIdForUpdate(caseId);
        verify(access).requireWrite(c);
        var order = inOrder(owners, audit);
        order.verify(owners).saveAndFlush(any(OwnerEntity.class));
        order.verify(audit).record(eq("OWNER"), any(UUID.class), eq("OWNER_CREATED"), anyMap());
    }
    @Test void duplicateNationalIdentityReturnsConflict() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        caseRow("REAL", CaseStatus.DRAFT, true);
        property("REAL");
        when(owners.existsByPropertyIdAndNationalIdIgnoreCase(propertyId, "0099887766"))
                .thenReturn(true);
        var request = new UpsertOwnerRequest("Ali", "Karimi", "0099887766", null, 1, 3, false);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.create(caseId, request)).getStatusCode());
        verify(owners, never()).saveAndFlush(any());
    }
    @Test void improperDenominatorIsRejectedEvenWithoutMvcValidation() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        caseRow("REAL", CaseStatus.DRAFT, true);
        property("REAL");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.create(caseId, request(1, 0))).getStatusCode());
    }

    @Test void exactShareTotalRejectsOverAllocationBeforeInsert() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        caseRow("REAL", CaseStatus.DRAFT, true);
        property("REAL");
        OwnerEntity existing = mock(OwnerEntity.class);
        when(existing.getOwnershipNumerator()).thenReturn(1);
        when(existing.getOwnershipDenominator()).thenReturn(3);
        when(owners.findByPropertyIdOrderByCreatedAtAsc(propertyId)).thenReturn(List.of(existing));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.create(caseId, request(3, 4))).getStatusCode());
        verify(owners, never()).saveAndFlush(any());
    }
    @Test void foreignOwnerIdCannotBeUpdatedOrDeleted() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        caseRow("REAL", CaseStatus.DRAFT, true);
        property("REAL");
        UUID foreignOwner = UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.update(caseId, foreignOwner, request(1, 2))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.delete(caseId, foreignOwner)).getStatusCode());
        verify(owners, never()).saveAndFlush(any());
        verify(owners, never()).delete(any());
    }
    @Test void updateFlushesBeforeEmittingAuditEvidence() {
        auth("creator", "OWNER_WRITE", "CASE_WRITE");
        caseRow("REAL", CaseStatus.DRAFT, true);
        property("REAL");
        UUID ownerId = UUID.randomUUID();
        OwnerEntity existing = mock(OwnerEntity.class);
        when(existing.getId()).thenReturn(ownerId);
        when(owners.findByIdAndPropertyId(ownerId, propertyId)).thenReturn(Optional.of(existing));
        when(owners.findByPropertyIdOrderByCreatedAtAsc(propertyId)).thenReturn(List.of(existing));
        service.update(caseId, ownerId, request(1, 2));
        var order = inOrder(owners, audit);
        order.verify(owners).flush();
        order.verify(audit).record(eq("OWNER"), eq(ownerId), eq("OWNER_UPDATED"), anyMap());
    }
}
