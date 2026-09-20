package com.sakhtyar.agents.orchestrator;

import com.sakhtyar.agents.core.AgentBus;
import com.sakhtyar.agents.core.AgentContracts;
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

        AgentResult persian = context.call(AgentType.PERSIAN, request);
        results.put(AgentType.PERSIAN.name(), persian);

        AgentIntent intent = AgentIntent.from(
                Objects.toString(persian.data().get("intent"), null)
        );
        AgentWorkflowType workflow = AgentWorkflowType.fromIntent(intent);
        Map<String, Object> normalized = AgentValues.map(
                persian.data().get("normalizedParameters")
        );
        // Explicit structured caller input always wins over AI extraction.
        AgentRequest effectiveRequest = request.mergeMissingParameters(normalized);

        if (persian.status() == AgentStatus.NEEDS_INPUT
                || persian.status() == AgentStatus.FAILED) {
            return new AgentWorkflowResult(
                    AgentContracts.SCHEMA_VERSION,
                    request.requestId(),
                    request.conversationId(),
                    request.caseId(),
                    intent,
                    workflow,
                    persian.status(),
                    persian.message(),
                    effectiveRequest.parameters(),
                    assumptions(results),
                    results,
                    persian.missingFields()
            );
        }

        for (AgentType type : steps(workflow)) {
            // Property may have been loaded before AI extraction only to provide
            // case context to PersianAgent. Re-run it with the effective request
            // so newly extracted fields are not lost. Other Agent results can be reused.
            AgentResult result = type == AgentType.PROPERTY
                    ? context.call(type, effectiveRequest)
                    : context.resultOf(type)
                            .orElseGet(() -> context.call(type, effectiveRequest));
            results.put(type.name(), result);
        }

        AgentStatus status = aggregateStatus(results.values().stream().toList());
        List<String> missing = results.values().stream()
                .flatMap(result -> result.missingFields().stream())
                .distinct()
                .toList();

        return new AgentWorkflowResult(
                AgentContracts.SCHEMA_VERSION,
                request.requestId(),
                request.conversationId(),
                request.caseId(),
                intent,
                workflow,
                status,
                buildMessage(workflow, status, missing),
                effectiveRequest.parameters(),
                assumptions(results),
                results,
                missing
        );
    }

    private List<String> assumptions(Map<String, AgentResult> results) {
        return results.values().stream()
                .flatMap(result -> AgentValues.stringList(
                        result.data().get("assumptions")
                ).stream())
                .distinct()
                .toList();
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
        if (failed == results.size()) return AgentStatus.FAILED;
        if (failed > 0) return AgentStatus.PARTIAL;
        if (results.stream().anyMatch(result -> result.status() == AgentStatus.NEEDS_INPUT)) {
            return AgentStatus.NEEDS_INPUT;
        }
        if (results.stream().anyMatch(result -> result.status() == AgentStatus.PARTIAL)) {
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
            return "تحلیل «" + workflow + "» شروع شد، اما برای ادامه این اطلاعات لازم است: "
                    + String.join("، ", missing);
        }
        if (status == AgentStatus.FAILED) {
            return "اجرای جریان «" + workflow + "» ناموفق بود. جزئیات در نتیجه Agentها موجود است.";
        }
        if (status == AgentStatus.PARTIAL) {
            return "جریان «" + workflow + "» به‌صورت بخشی اجرا شد. هشدارها و داده‌های ناقص را بررسی کنید.";
        }
        return "جریان «" + workflow + "» با موفقیت اجرا شد.";
    }
}
