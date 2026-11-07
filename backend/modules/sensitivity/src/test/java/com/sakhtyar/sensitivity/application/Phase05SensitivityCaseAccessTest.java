package com.sakhtyar.sensitivity.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.finance.domain.FinancialAnalysisRepository;
import com.sakhtyar.sensitivity.domain.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase05SensitivityCaseAccessTest {
    @Test void unauthorizedDirectUuidReadCannotLoadSensitivityPoints() {
        var analyses=mock(SensitivityAnalysisRepository.class);
        var points=mock(SensitivityPointRepository.class);
        var access=mock(CaseAccessService.class);
        var entity=mock(SensitivityAnalysisEntity.class);
        UUID id=UUID.randomUUID(),caseId=UUID.randomUUID();
        when(analyses.findById(id)).thenReturn(Optional.of(entity));
        when(entity.getCaseId()).thenReturn(caseId);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(caseId);
        var svc=new SensitivityService(analyses,points,mock(FinancialAnalysisRepository.class),
            mock(AuditService.class),access);
        assertThrows(ResponseStatusException.class,()->svc.get(id));
        verifyNoInteractions(points);
    }
}
