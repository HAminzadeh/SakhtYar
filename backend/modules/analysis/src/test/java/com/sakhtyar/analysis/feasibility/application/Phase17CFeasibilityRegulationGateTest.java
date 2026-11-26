package com.sakhtyar.analysis.feasibility.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.regulation.domain.*;
import com.sakhtyar.scenario.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class Phase17CFeasibilityRegulationGateTest {
    private final UUID caseId=UUID.randomUUID(), propertyId=UUID.randomUUID(), scenarioId=UUID.randomUUID();
    private final Instant time=Instant.parse("2026-11-26T09:00:00Z");
    private final PropertyEntity property=mock(PropertyEntity.class);
    private final ConstructionScenarioEntity scenario=mock(ConstructionScenarioEntity.class);
    private final ScenarioCostSnapshotEntity cost=mock(ScenarioCostSnapshotEntity.class);
    private final UrbanEvaluationEntity urban=mock(UrbanEvaluationEntity.class);
    private final UrbanRuleRepository rules=mock(UrbanRuleRepository.class);

    private void validInputs() {
        when(property.getId()).thenReturn(propertyId);
        when(property.getCaseId()).thenReturn(caseId);
        when(property.getDataScope()).thenReturn("REAL");
        when(property.getProvince()).thenReturn("تهران");
        when(property.getCity()).thenReturn("تهران");
        when(property.getUpdatedAt()).thenReturn(time);
        when(scenario.getId()).thenReturn(scenarioId);
        when(scenario.getUpdatedAt()).thenReturn(time);
        when(cost.getScenarioId()).thenReturn(scenarioId);
        when(cost.getCalculatedAt()).thenReturn(time.plusSeconds(30));
        when(cost.getProvince()).thenReturn("تهران");
        when(cost.getCity()).thenReturn("تهران");
        when(urban.getPropertyId()).thenReturn(propertyId);
        when(urban.getScenarioId()).thenReturn(scenarioId);
        when(urban.getEvaluatedAt()).thenReturn(time.plusSeconds(60));
        when(urban.getStatus()).thenReturn(EvaluationStatus.REVIEW_REQUIRED);
        when(urban.getInputSnapshot()).thenReturn(Map.of(
            "caseId", caseId.toString(), "propertyId", propertyId.toString(),
            "scenarioId", scenarioId.toString(), "propertyUpdatedAt", time.toString(),
            "scenarioUpdatedAt", time.toString(), "ruleSnapshots", List.of()));
    }
    @Test void currentReviewRequiredSnapshotCanProduceOnlyNonfinalAnalysis() {
        validInputs();
        assertDoesNotThrow(() -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of()));
        verifyNoInteractions(rules);
    }
    @Test void oldSnapshotWithoutVersionMarkersIsRejected() {
        validInputs();
        when(urban.getInputSnapshot()).thenReturn(Map.of("propertyId",propertyId.toString(), "ruleSnapshots", List.of()));
        assertThrows(ResponseStatusException.class, () -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of()));
    }
    @Test void changedScenarioInvalidatesCostAndRegulation() {
        validInputs();
        when(scenario.getUpdatedAt()).thenReturn(time.plusSeconds(100));
        assertThrows(ResponseStatusException.class, () -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of()));
    }
    @Test void crossScenarioEvaluationCannotBeConsumed() {
        validInputs();
        when(urban.getScenarioId()).thenReturn(UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of()));
    }
    @Test void testScopeNeverReachesRules() {
        validInputs();
        when(property.getDataScope()).thenReturn("TEST");
        assertThrows(ResponseStatusException.class, () -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of()));
        verifyNoInteractions(rules);
    }
    @Test void aCostSnapshotForAnotherCityIsRejected() {
        validInputs();
        when(cost.getCity()).thenReturn("اصفهان");
        assertThrows(ResponseStatusException.class, () -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of()));
    }
    @Test void unsupportedCompliantWithoutCertificateIsBlocked() {
        validInputs();
        when(urban.getStatus()).thenReturn(EvaluationStatus.COMPLIANT);
        assertThrows(ResponseStatusException.class, () -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of()));
    }
    @Test void revokedRuleBlocksEvenHistoricSnapshotReusedToday() {
        validInputs();
        UUID ruleId=UUID.randomUUID();
        when(urban.getInputSnapshot()).thenReturn(Map.of(
            "caseId",caseId.toString(), "propertyId",propertyId.toString(),
            "scenarioId",scenarioId.toString(), "propertyUpdatedAt",time.toString(),
            "scenarioUpdatedAt",time.toString(), "ruleSnapshots",List.of(Map.of("id",ruleId.toString(),
                "updatedAt",time.toString(), "verifiedKnowledgeRuleId",UUID.randomUUID().toString()))));
        UrbanRuleEntity revoked=mock(UrbanRuleEntity.class);
        when(revoked.isActive()).thenReturn(false);
        when(rules.findById(ruleId)).thenReturn(Optional.of(revoked));
        assertThrows(ResponseStatusException.class, () -> FeasibilityRegulationGate.requireFresh(property,scenario,cost,urban,rules,Set.of(ruleId)));
    }
}
