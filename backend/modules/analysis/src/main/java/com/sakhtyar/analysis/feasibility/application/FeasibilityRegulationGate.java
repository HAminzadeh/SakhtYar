package com.sakhtyar.analysis.feasibility.application;

import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.regulation.domain.EvaluationStatus;
import com.sakhtyar.regulation.domain.UrbanEvaluationEntity;
import com.sakhtyar.regulation.domain.UrbanRuleEntity;
import com.sakhtyar.regulation.domain.UrbanRuleRepository;
import com.sakhtyar.scenario.domain.ConstructionScenarioEntity;
import com.sakhtyar.scenario.domain.ScenarioCostSnapshotEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service-side, fail-closed verification before historic regulation evaluations
 * can be consumed by financial/feasibility decisions. This does not certify
 * legal completeness or create a municipal permit.
 */
public final class FeasibilityRegulationGate {
    private FeasibilityRegulationGate() {}

    public static void requireFresh(PropertyEntity property, ConstructionScenarioEntity scenario,
            ScenarioCostSnapshotEntity cost, UrbanEvaluationEntity evaluation, UrbanRuleRepository rules, Set<UUID> currentlyAuthorizedRuleIds) {
        if (property == null || scenario == null || cost == null || evaluation == null || rules == null || currentlyAuthorizedRuleIds == null)
            stale();
        if (!"REAL".equals(property.getDataScope())) stale();
        if (!Objects.equals(evaluation.getPropertyId(), property.getId()) ||
            !Objects.equals(evaluation.getScenarioId(), scenario.getId()) ||
            !Objects.equals(cost.getScenarioId(), scenario.getId())) stale();
        if (property.getUpdatedAt() == null || scenario.getUpdatedAt() == null ||
            cost.getCalculatedAt() == null || evaluation.getEvaluatedAt() == null) stale();
        if (cost.getCalculatedAt().isBefore(scenario.getUpdatedAt())) stale();
        // A cost quote from another market/city cannot be used for this parcel.
        if (!samePlace(property.getProvince(),cost.getProvince()) ||
            !samePlace(property.getCity(),cost.getCity())) stale();
        Map<String,Object> snapshot = evaluation.getInputSnapshot();
        if (snapshot == null || snapshot.isEmpty()) stale();
        if (!Objects.equals(Objects.toString(snapshot.get("propertyId"), ""), property.getId().toString()) ||
            !Objects.equals(Objects.toString(snapshot.get("caseId"), ""), property.getCaseId().toString()) ||
            !Objects.equals(Objects.toString(snapshot.get("scenarioId"), ""), scenario.getId().toString()) ||
            !Objects.equals(Objects.toString(snapshot.get("propertyUpdatedAt"), ""), property.getUpdatedAt().toString()) ||
            !Objects.equals(Objects.toString(snapshot.get("scenarioUpdatedAt"), ""), scenario.getUpdatedAt().toString())) stale();
        if (evaluation.getEvaluatedAt().isBefore(property.getUpdatedAt()) ||
            evaluation.getEvaluatedAt().isBefore(scenario.getUpdatedAt())) stale();
        Object raw = snapshot.get("ruleSnapshots");
        if (!(raw instanceof List<?> entries)) stale();
        for (Object obj : (List<?>) raw) {
            if (!(obj instanceof Map<?,?>)) stale();
            Map<?,?> row=(Map<?,?>) obj;
            try {
                UUID id=UUID.fromString(Objects.toString(row.get("id"), ""));
                if (!currentlyAuthorizedRuleIds.contains(id)) stale();
                UrbanRuleEntity current=rules.findById(id).orElse(null);
                if (current == null || !current.isActive() || current.getVerifiedKnowledgeRuleId() == null ||
                    !Objects.equals(current.getUpdatedAt().toString(), Objects.toString(row.get("updatedAt"), "")) ||
                    !Objects.equals(current.getVerifiedKnowledgeRuleId().toString(),
                        Objects.toString(row.get("verifiedKnowledgeRuleId"), ""))) stale();
            } catch (IllegalArgumentException | NullPointerException ex) {
                stale();
            }
        }
        // No evaluated rule, or a numeric-only result with no legal coverage certificate,
        // can ever establish project-wide regulatory compliance.
        if (evaluation.getStatus() == EvaluationStatus.COMPLIANT &&
            (!Boolean.TRUE.equals(snapshot.get("legalCoverageCertified")) ||
             ((List<?>) raw).isEmpty())) stale();
    }

    private static boolean samePlace(String a,String b) {
        return a!=null && !a.isBlank() && b!=null && !b.isBlank() &&
            a.trim().equalsIgnoreCase(b.trim());
    }

    private static void stale() {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Regulation or cost snapshot is stale or lacks verifiable legal coverage; re-evaluation required.");
    }
}
