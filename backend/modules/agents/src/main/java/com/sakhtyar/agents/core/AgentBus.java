package com.sakhtyar.agents.core;

import com.sakhtyar.audit.application.AuditService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentBus {

    private static final Logger log = LoggerFactory.getLogger(AgentBus.class);
    private static final int MAX_DEPTH = 12;

    private final AgentRegistry registry;
    private final AuditService auditService;

    public AgentBus(AgentRegistry registry, AuditService auditService) {
        this.registry = registry;
        this.auditService = auditService;
    }

    public AgentExecutionContext newExecutionContext() {
        return AgentExecutionContext.root(this);
    }

    public AgentResult invoke(AgentType type, AgentRequest request) {
        return invoke(type, request, newExecutionContext());
    }

    public AgentResult invoke(
            AgentType type,
            AgentRequest request,
            AgentExecutionContext context
    ) {
        if (context.contains(type)) {
            return AgentResult.failed(
                    type,
                    "چرخه فراخوانی بین Agentها شناسایی شد: " + context.callChain()
            );
        }
        if (context.depth() >= MAX_DEPTH) {
            return AgentResult.failed(type, "حداکثر عمق فراخوانی Agentها رد شد.");
        }

        AgentExecutionContext child = context.enter(type);
        Instant startedAt = Instant.now();
        AgentResult result;

        try {
            result = registry.require(type).execute(request, child);
            if (result == null) {
                result = AgentResult.failed(type, "Agent نتیجه‌ای برنگرداند.");
            }
        } catch (Exception ex) {
            log.warn("Agent {} failed for request {}", type, request.requestId(), ex);
            result = AgentResult.failed(type, "اجرای Agent با خطا مواجه شد: " + ex.getMessage());
        }

        context.remember(type, result);
        audit(type, request, result, startedAt);
        return result;
    }

    private void audit(
            AgentType type,
            AgentRequest request,
            AgentResult result,
            Instant startedAt
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", type.name());
        payload.put("status", result.status().name());
        payload.put("conversationId", request.conversationId().toString());
        payload.put("startedAt", startedAt.toString());
        payload.put("completedAt", result.completedAt().toString());
        if (request.caseId() != null) {
            payload.put("caseId", request.caseId().toString());
        }
        try {
            auditService.record(
                    "AGENT_EXECUTION",
                    request.requestId(),
                    "AGENT_" + type.name() + "_" + result.status().name(),
                    payload
            );
        } catch (RuntimeException ex) {
            log.warn("Could not persist agent audit event", ex);
        }
    }
}
