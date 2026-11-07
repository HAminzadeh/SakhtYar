package com.sakhtyar.finance.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.finance.domain.*;
import com.sakhtyar.owner.domain.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase05FinanceCaseAccessTest {
    @Test void unauthorizedDirectUuidReadCannotLoadAllocations() {
        var analyses=mock(FinancialAnalysisRepository.class);
        var allocations=mock(FinancialParticipationAllocationRepository.class);
        var access=mock(CaseAccessService.class);
        var entity=mock(FinancialAnalysisEntity.class);
        UUID id=UUID.randomUUID(),caseId=UUID.randomUUID();
        when(analyses.findById(id)).thenReturn(Optional.of(entity));
        when(entity.getCaseId()).thenReturn(caseId);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(caseId);
        var svc=new FinancialAnalysisService(analyses,allocations,mock(FeasibilityAssessmentRepository.class),
            mock(OwnerRepository.class),mock(ProjectBuilderRepository.class),mock(AuditService.class),access);
        assertThrows(ResponseStatusException.class,()->svc.get(id));
        verifyNoInteractions(allocations);
    }
}
