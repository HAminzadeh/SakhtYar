package com.sakhtyar.contract.application;

import static com.sakhtyar.contract.api.ContractDtos.UpsertContractRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.*;
import com.sakhtyar.contract.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ContractServiceSecurityTest {
    ContractRecordRepository records;
    ContractRevisionRepository versions;
    CaseRepository cases;
    CaseAccessService access;
    AuditService audit;
    ContractService service;
    UUID caseId,contractId;
    CaseEntity parent;
    ContractRecordEntity draft;

    @BeforeEach void setup() {
        records=mock(ContractRecordRepository.class);
        versions=mock(ContractRevisionRepository.class);
        cases=mock(CaseRepository.class);
        access=mock(CaseAccessService.class);
        audit=mock(AuditService.class);
        service=new ContractService(records,versions,cases,access,audit);
        caseId=UUID.randomUUID();contractId=UUID.randomUUID();
        parent=new CaseEntity(caseId,"PRJ-1","Synthetic",CaseStatus.DRAFT,null,null,null,null,
                null,null,"alice",Instant.now(),Instant.now());
        ReflectionTestUtils.setField(parent,"dataScope","REAL");
        draft=new ContractRecordEntity(contractId,caseId,"CT-1","PARTICIPATION_IN_CONSTRUCTION",
                "DRAFT","Synthetic",null,null,null,"1.0",Map.of("PARTIES","Synthetic parties"),
                Map.of(),"alice",Instant.now(),Instant.now());
        when(cases.findById(caseId)).thenReturn(Optional.of(parent));
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(parent));
        when(records.findById(contractId)).thenReturn(Optional.of(draft));
        when(records.findCaseIdById(contractId)).thenReturn(Optional.of(caseId));
        when(versions.findTopByContractIdOrderByRevisionNoDesc(contractId)).thenReturn(Optional.empty());
        login("CASE_READ","CASE_WRITE");
    }
    @AfterEach void tearDown(){SecurityContextHolder.clearContext();}
    void login(String... perms) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "alice","n/a",AuthorityUtils.createAuthorityList(perms)));
    }
    UpsertContractRequest req() {
        return new UpsertContractRequest("CT-1","PARTICIPATION_IN_CONSTRUCTION","DRAFT",
                "Synthetic draft",null,null,null,"1.0",Map.of("PARTIES","Synthetic parties"),Map.of());
    }
    @Test void contractReadPermissionRequiredInsideService() {
        login("CASE_WRITE");
        assertThrows(ResponseStatusException.class,()->service.byCase(caseId));
        verify(records,never()).findByCaseIdOrderByUpdatedAtDesc(any());
    }
    @Test void testerCannotReadAnyRealContract() {
        login("ROLE_TESTER","CASE_READ","CASE_WRITE");
        assertThrows(ResponseStatusException.class,()->service.get(contractId));
        verify(access,never()).requireRead(any(CaseEntity.class));
    }
    @Test void nonMemberIsNotAbleToList() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(parent);
        assertThrows(ResponseStatusException.class,()->service.byCase(caseId));
        verify(records,never()).findByCaseIdOrderByUpdatedAtDesc(any());
    }
    @Test void directIdReadMustEnforceCaseMembership() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(parent);
        assertThrows(ResponseStatusException.class,()->service.get(contractId));
    }
    @Test void reviewMustAuthorizeBeforeQuery() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(parent);
        assertThrows(ResponseStatusException.class,()->service.review(caseId));
        verify(records,never()).findByCaseIdOrderByUpdatedAtDesc(any());
    }
    @Test void testScopeIsRejected() {
        ReflectionTestUtils.setField(parent,"dataScope","TEST");
        assertThrows(ResponseStatusException.class,()->service.byCase(caseId));
        verify(records,never()).findByCaseIdOrderByUpdatedAtDesc(any());
    }
    @Test void createMustRequireCaseWriteAndParentLock() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireWrite(parent);
        assertThrows(ResponseStatusException.class,()->service.create(caseId,req(),null));
        verify(cases).findByIdForUpdate(caseId);
        verify(records,never()).saveAndFlush(any());
    }
    @Test void createdDraftHasExactlyOneRevisionAndOneAudit() {
        service.create(caseId,req(),null);
        verify(records).saveAndFlush(any(ContractRecordEntity.class));
        verify(versions).saveAndFlush(argThat(v->v.getRevisionNo()==1 && "CREATED".equals(v.getEventCode())));
        verify(audit).record(eq("CONTRACT"),any(UUID.class),eq("CONTRACT_CREATED"),any());
    }
    @Test void archivedCaseCannotBeModified() {
        ReflectionTestUtils.setField(parent,"status",CaseStatus.ARCHIVED);
        assertThrows(ResponseStatusException.class,()->service.create(caseId,req(),null));
        verifyNoInteractions(audit);
    }
    @Test void signedLegacyRecordIsImmutable() {
        ReflectionTestUtils.setField(draft,"status","SIGNED");
        assertThrows(ResponseStatusException.class,()->service.update(contractId,req()));
        verify(versions,never()).saveAndFlush(any());
    }
    @Test void optimisticIfMatchRejectsStaleEdits() {
        assertEquals(HttpStatus.PRECONDITION_FAILED,assertThrows(ResponseStatusException.class,
                ()->service.update(contractId,req(),"\"7\"")).getStatusCode());
        verify(versions,never()).saveAndFlush(any());
    }
    @Test void legacyDraftUpdatePreservesBaselineBeforeNewRevision() {
        service.update(contractId,req(),"\"1\"");
        verify(versions,times(2)).saveAndFlush(any(ContractRevisionEntity.class));
        verify(versions).saveAndFlush(argThat(v->v.getRevisionNo()==1 && "BASELINE_CAPTURED".equals(v.getEventCode())));
        verify(versions).saveAndFlush(argThat(v->v.getRevisionNo()==2 && "UPDATED".equals(v.getEventCode())));
        verify(audit).record(eq("CONTRACT"),eq(contractId),eq("CONTRACT_UPDATED"),any());
    }
    @Test void versionsAreSubjectToReadPermission() {
        login("CASE_WRITE");
        assertThrows(ResponseStatusException.class,()->service.versions(contractId));
        verify(versions,never()).findByContractIdOrderByRevisionNoAsc(any());
    }
    @Test void reviewIgnoresKeywordsOnlyInValueText() {
        when(records.findByCaseIdOrderByUpdatedAtDesc(caseId)).thenReturn(List.of(draft));
        var result=service.review(caseId);
        assertTrue(result.presentClauses().contains("PARTIES"));
        assertTrue(result.missingClauses().contains("TERMINATION"));
        assertTrue(result.disclaimer().contains("حقوقی"));
    }
}
