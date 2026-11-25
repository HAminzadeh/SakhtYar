package com.sakhtyar.regulation.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.globalization.application.JurisdictionService;
import com.sakhtyar.knowledge.domain.KnowledgeSourceRepository;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import com.sakhtyar.regulation.api.RegulationDtos.EvaluateRequest;
import com.sakhtyar.regulation.domain.*;
import com.sakhtyar.scenario.domain.ConstructionScenarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class Phase17RegulationAuthorizationTest {
    private final UrbanRuleRepository rules=mock(UrbanRuleRepository.class);
    private final UrbanEvaluationRepository evaluations=mock(UrbanEvaluationRepository.class);
    private final UrbanEvaluationResultRepository results=mock(UrbanEvaluationResultRepository.class);
    private final PropertyRepository properties=mock(PropertyRepository.class);
    private final ConstructionScenarioRepository scenarios=mock(ConstructionScenarioRepository.class);
    private final CaseAccessService access=mock(CaseAccessService.class);
    private final RegulationService service=new RegulationService(rules,evaluations,results,properties,
            scenarios,mock(KnowledgeSourceRepository.class),mock(AuditService.class),
            mock(JurisdictionService.class),mock(RegulationEvidenceService.class),access);

    @Test void unauthorizedUserCannotEvaluateOrReadScenario() {
        UUID p=UUID.randomUUID(), c=UUID.randomUUID();
        PropertyEntity property=mock(PropertyEntity.class);
        when(property.getCaseId()).thenReturn(c);
        when(properties.findById(p)).thenReturn(Optional.of(property));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireWrite(c);
        assertThrows(ResponseStatusException.class,()->service.evaluate(new EvaluateRequest(p,UUID.randomUUID()),null));
        verifyNoInteractions(scenarios,rules,evaluations,results);
    }
    @Test void unauthorizedUserCannotEnumerateHistory() {
        UUID p=UUID.randomUUID(),c=UUID.randomUUID();
        PropertyEntity property=mock(PropertyEntity.class);
        when(property.getCaseId()).thenReturn(c);
        when(properties.findById(p)).thenReturn(Optional.of(property));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(c);
        assertThrows(ResponseStatusException.class,()->service.evaluations(p));
        verifyNoInteractions(evaluations,results);
    }
    @Test void unauthorizedUserCannotReadIndividualEvaluationDetails() {
        UUID p=UUID.randomUUID(),c=UUID.randomUUID(), e=UUID.randomUUID();
        PropertyEntity property=mock(PropertyEntity.class);
        UrbanEvaluationEntity evaluation=mock(UrbanEvaluationEntity.class);
        when(evaluation.getPropertyId()).thenReturn(p);
        when(evaluations.findById(e)).thenReturn(Optional.of(evaluation));
        when(property.getCaseId()).thenReturn(c);
        when(properties.findById(p)).thenReturn(Optional.of(property));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(c);
        assertThrows(ResponseStatusException.class,()->service.evaluation(e));
        verifyNoInteractions(results);
    }
    @Test void testScopedPropertyNeverReachesCalculation() {
        UUID p=UUID.randomUUID(),c=UUID.randomUUID();
        PropertyEntity property=mock(PropertyEntity.class);
        when(property.getCaseId()).thenReturn(c);
        when(property.getDataScope()).thenReturn("TEST");
        when(properties.findById(p)).thenReturn(Optional.of(property));
        assertThrows(ResponseStatusException.class,()->service.evaluate(new EvaluateRequest(p,null),null));
        verifyNoInteractions(scenarios,rules,evaluations,results);
    }
}
