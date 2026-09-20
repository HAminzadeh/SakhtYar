package com.sakhtyar.agents.orchestrator;

import com.sakhtyar.agents.core.AgentBus;
import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentIntent;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.AgentWorkflowType;
import com.sakhtyar.agents.support.AgentValues;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private final AgentBus bus;

    public AgentOrchestrator(AgentBus bus) {
        this.bus = bus;
    }

    public AgentWorkflowResult execute(AgentRequest request) {
        AgentExecutionContext context = bus.newExecutionContext();
        LinkedHashMap<String, AgentResult> results = new LinkedHashMap<>();

        AgentResult persian = bus.invoke(AgentType.PERSIAN, request, context);
        results.put(AgentType.PERSIAN.name(), persian);

        AgentIntent intent = AgentIntent.from(
                Objects.toString(persian.data().get("intent"), null)
        );
        AgentWorkflowType workflow = AgentWorkflowType.fromIntent(intent);

        Map<String, Object> normalized = AgentValues.map(
                persian.data().get("normalizedParameters")
        );

        // AI extraction can fill missing values, but explicit structured input from
        // the caller must always win.
        AgentRequest effectiveRequest = request.mergeMissingParameters(normalized);

        for (AgentType type : steps(workflow)) {
            AgentResult result = bus.invoke(type, effectiveRequest, context);
            results.put(type.name(), result);
        }

        AgentStatus status = aggregateStatus(results.values().stream().toList());
        List<String> missing = results.values().stream()
                .flatMap(result -> result.missingFields().stream())
                .distinct()
                .toList();

        String message = buildMessage(workflow, status, missing);
        return new AgentWorkflowResult(
                request.requestId(),
                request.conversationId(),
                intent,
                workflow,
                status,
                message,
                results,
                missing
        );
    }

    private List<AgentType> steps(AgentWorkflowType workflow) {
        return switch (workflow) {
            case PROPERTY_ANALYSIS -> List.of(AgentType.PROPERTY);
            case PROPERTY_VALUATION -> List.of(
                    AgentType.PROPERTY,
                    AgentType.VALUATION
            );
            case BUILDABILITY_ANALYSIS -> List.of(
                    AgentType.PROPERTY,
                    AgentType.MUNICIPALITY,
                    AgentType.CONSTRUCTION
            );
            case PARTNERSHIP_ANALYSIS -> List.of(
                    AgentType.PROPERTY,
                    AgentType.MUNICIPALITY,
                    AgentType.CONSTRUCTION,
                    AgentType.VALUATION,
                    AgentType.FINANCIAL
            );
            case CONTRACT_REVIEW -> List.of(
                    AgentType.CONTRACT,
                    AgentType.LEGAL
            );
            case BUILDER_MATCHING -> List.of(AgentType.MATCHING);
            case RESEARCH -> List.of(AgentType.RESEARCH);
        };
    }

    private AgentStatus aggregateStatus(List<AgentResult> results) {
        long failed = results.stream()
                .filter(result -> result.status() == AgentStatus.FAILED)
                .count();
        if (failed == results.size()) {
            return AgentStatus.FAILED;
        }
        if (failed > 0) {
            return AgentStatus.PARTIAL;
        }
        if (results.stream().anyMatch(result ->
                result.status() == AgentStatus.NEEDS_INPUT)) {
            return AgentStatus.NEEDS_INPUT;
        }
        if (results.stream().anyMatch(result ->
                result.status() == AgentStatus.PARTIAL)) {
            return AgentStatus.PARTIAL;
        }
        return AgentStatus.SUCCESS;
    }

    private String buildMessage(
            AgentWorkflowType workflow,
            AgentStatus status,
            List<String> missing
    ) {
        if (status == AgentStatus.NEEDS_INPUT && !missing.isEmpty()) {
            return "\u062A\u062D\u0644\u06CC\u0644 \u00AB" + workflow + "\u00BB \u0634\u0631\u0648\u0639 \u0634\u062F\u060C \u0627\u0645\u0627 \u0628\u0631\u0627\u06CC \u0627\u062F\u0627\u0645\u0647 \u0627\u06CC\u0646 \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0644\u0627\u0632\u0645 \u0627\u0633\u062A: "
                    + String.join("\u060C ", missing);
        }
        if (status == AgentStatus.FAILED) {
            return "\u0627\u062C\u0631\u0627\u06CC \u062C\u0631\u06CC\u0627\u0646 \u00AB" + workflow + "\u00BB \u0646\u0627\u0645\u0648\u0641\u0642 \u0628\u0648\u062F. \u062C\u0632\u0626\u06CC\u0627\u062A \u062F\u0631 \u0646\u062A\u06CC\u062C\u0647 Agent\u0647\u0627 \u0645\u0648\u062C\u0648\u062F \u0627\u0633\u062A.";
        }
        if (status == AgentStatus.PARTIAL) {
            return "\u062C\u0631\u06CC\u0627\u0646 \u00AB" + workflow + "\u00BB \u0628\u0647\u200C\u0635\u0648\u0631\u062A \u0628\u062E\u0634\u06CC \u0627\u062C\u0631\u0627 \u0634\u062F. \u0647\u0634\u062F\u0627\u0631\u0647\u0627 \u0648 \u062F\u0627\u062F\u0647\u200C\u0647\u0627\u06CC \u0646\u0627\u0642\u0635 \u0631\u0627 \u0628\u0631\u0631\u0633\u06CC \u06A9\u0646\u06CC\u062F.";
        }
        return "\u062C\u0631\u06CC\u0627\u0646 \u00AB" + workflow + "\u00BB \u0628\u0627 \u0645\u0648\u0641\u0642\u06CC\u062A \u0627\u062C\u0631\u0627 \u0634\u062F.";
    }
}
