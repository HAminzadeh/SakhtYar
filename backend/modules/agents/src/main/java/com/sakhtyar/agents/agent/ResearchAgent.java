package com.sakhtyar.agents.agent;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.SakhtyarAgent;
import com.sakhtyar.agents.support.AgentValues;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ResearchAgent implements SakhtyarAgent {

    @Override
    public AgentType type() {
        return AgentType.RESEARCH;
    }

    @Override
    public String displayName() {
        return "عامل تحقیق";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        List<String> requestedDomains = AgentValues.stringList(
                request.parameters().get("researchDomains")
        );
        if (requestedDomains.isEmpty()) {
            requestedDomains = List.of("PROPERTY", "MUNICIPALITY", "VALUATION");
        }

        LinkedHashMap<String, Object> collected = new LinkedHashMap<>();
        ArrayList<String> warnings = new ArrayList<>();
        boolean partial = false;

        for (String domain : requestedDomains) {
            AgentType target = toAgentType(domain);
            if (target == null || target == AgentType.RESEARCH || target == AgentType.PERSIAN) {
                warnings.add("دامنه تحقیق ناشناخته یا غیرقابل فراخوانی: " + domain);
                partial = true;
                continue;
            }
            AgentResult result = context.resultOf(target)
                    .orElseGet(() -> context.call(target, request));
            collected.put(target.name(), Map.of(
                    "status", result.status().name(),
                    "data", result.data(),
                    "warnings", result.warnings(),
                    "missingFields", result.missingFields()
            ));
            if (result.status() != AgentStatus.SUCCESS) {
                partial = true;
            }
        }

        Object externalSources = request.parameters().get("externalSources");
        if (externalSources != null) {
            collected.put("externalSources", externalSources);
        } else {
            warnings.add("منبع تحقیق بیرونی به Backend متصل نشده است؛ این Agent فعلاً Agentهای داخلی را تجمیع می‌کند.");
            partial = true;
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("requestedDomains", requestedDomains);
        data.put("collected", collected);

        return new AgentResult(
                type(),
                partial ? AgentStatus.PARTIAL : AgentStatus.SUCCESS,
                "نتایج Agentهای تخصصی برای تحقیق تجمیع شد.",
                data,
                warnings,
                List.of(),
                externalSources == null ? 0.65d : 0.85d,
                Instant.now()
        );
    }

    private AgentType toAgentType(String domain) {
        if (domain == null) {
            return null;
        }
        try {
            return AgentType.valueOf(domain.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return switch (domain.trim().toLowerCase(Locale.ROOT)) {
                case "ملک" -> AgentType.PROPERTY;
                case "شهرداری", "ضوابط" -> AgentType.MUNICIPALITY;
                case "قیمت", "ارزش" -> AgentType.VALUATION;
                case "ساخت" -> AgentType.CONSTRUCTION;
                case "مالی" -> AgentType.FINANCIAL;
                case "قرارداد" -> AgentType.CONTRACT;
                case "حقوقی" -> AgentType.LEGAL;
                case "سازنده", "تطبیق" -> AgentType.MATCHING;
                default -> null;
            };
        }
    }
}
