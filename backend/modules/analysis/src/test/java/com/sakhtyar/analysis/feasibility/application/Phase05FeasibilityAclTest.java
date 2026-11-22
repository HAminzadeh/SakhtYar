package com.sakhtyar.analysis.feasibility.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.analysis.feasibility.api.FeasibilityDtos.CreateAssessmentRequest;
import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.property.domain.PropertyRepository;
import com.sakhtyar.regulation.domain.UrbanEvaluationRepository;
import com.sakhtyar.scenario.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase05FeasibilityAclTest {
    private final FeasibilityAssessmentRepository repo=mock(FeasibilityAssessmentRepository.class);
    private final FeasibilityReasonRepository reasons=mock(FeasibilityReasonRepository.class);
    private final CaseAccessService acl=mock(CaseAccessService.class);
    private final FeasibilityService service = new FeasibilityService(repo,reasons,mock(CaseRepository.class),
            mock(PropertyRepository.class),mock(ConstructionScenarioRepository.class),
            mock(ScenarioCostSnapshotRepository.class),mock(UrbanEvaluationRepository.class),
            mock(AuditService.class),acl);

    @Test void unauthorizedCreationRejectedBeforePersistence() {
        UUID caseId=UUID.randomUUID();
        var request=new CreateAssessmentRequest(caseId,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        doThrow(new SecurityException("deny")).when(acl).requireWrite(caseId);
        assertThrows(SecurityException.class,()->service.create(request,null));
        verifyNoInteractions(repo,reasons);
    }
    @Test void unauthorizedCaseListRejectedBeforeRepositoryQuery() {
        UUID caseId=UUID.randomUUID();
        doThrow(new SecurityException("deny")).when(acl).requireRead(caseId);
        assertThrows(SecurityException.class,()->service.byCase(caseId));
        verifyNoInteractions(repo,reasons);
    }
    @Test void unauthorizedGetRejectedBeforeReasonLookup() {
        UUID id=UUID.randomUUID(),caseId=UUID.randomUUID();
        var entity=mock(FeasibilityAssessmentEntity.class);
        when(entity.getCaseId()).thenReturn(caseId);
        when(repo.findById(id)).thenReturn(Optional.of(entity));
        doThrow(new SecurityException("deny")).when(acl).requireRead(caseId);
        assertThrows(SecurityException.class,()->service.get(id));
        verifyNoInteractions(reasons);
    }
    @Test void byScenarioExcludesUnauthorizedCases() {
        UUID scenarioId=UUID.randomUUID();
        var entity=mock(FeasibilityAssessmentEntity.class);
        when(entity.getCaseId()).thenReturn(UUID.randomUUID());
        when(repo.findByScenarioIdOrderByAssessedAtDesc(scenarioId)).thenReturn(List.of(entity));
        assertTrue(service.byScenario(scenarioId).isEmpty());
        verifyNoInteractions(reasons);
    }
}