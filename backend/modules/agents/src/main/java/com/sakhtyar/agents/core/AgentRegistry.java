package com.sakhtyar.agents.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentRegistry {

    private final Map<AgentType, SakhtyarAgent> agents;

    public AgentRegistry(List<SakhtyarAgent> registeredAgents) {
        EnumMap<AgentType, SakhtyarAgent> map = new EnumMap<>(AgentType.class);
        for (SakhtyarAgent agent : registeredAgents) {
            SakhtyarAgent previous = map.put(agent.type(), agent);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate agent registration for " + agent.type()
                );
            }
        }
        this.agents = Collections.unmodifiableMap(map);
    }

    public SakhtyarAgent require(AgentType type) {
        SakhtyarAgent agent = agents.get(type);
        if (agent == null) {
            throw new IllegalStateException("Agent is not registered: " + type);
        }
        return agent;
    }

    public List<AgentType> availableTypes() {
        return agents.keySet().stream().sorted().toList();
    }

    public List<AgentDescriptor> descriptors() {
        return agents.values().stream()
                .map(this::descriptor)
                .sorted((a, b) -> a.type().compareTo(b.type()))
                .toList();
    }

    private AgentDescriptor descriptor(SakhtyarAgent agent) {
        AgentType type = agent.type();
        return new AgentDescriptor(
                type,
                agent.displayName(),
                agent.version(),
                capabilities(type),
                Map.of(
                        "schemaVersion", AgentContracts.SCHEMA_VERSION,
                        "type", "AgentRequest"
                ),
                Map.of(
                        "schemaVersion", AgentContracts.SCHEMA_VERSION,
                        "type", "AgentResult"
                ),
                List.of("AUTHENTICATED"),
                type == AgentType.PERSIAN ? 90 : 20,
                provider(type)
        );
    }

    private List<String> capabilities(AgentType type) {
        return switch (type) {
            case PERSIAN -> List.of(
                    "PERSIAN_NORMALIZATION",
                    "INTENT_DETECTION",
                    "ENTITY_EXTRACTION",
                    "GLOSSARY_CLARIFICATION"
            );
            case PROPERTY -> List.of("CASE_PROPERTY_CONTEXT", "PROPERTY_NORMALIZATION");
            case MUNICIPALITY -> List.of("URBAN_RULE_INPUT_NORMALIZATION");
            case CONSTRUCTION -> List.of("DETERMINISTIC_BUILDABILITY_CALCULATION");
            case VALUATION -> List.of("SOURCE_BASED_VALUATION", "COMPARABLE_MEDIAN");
            case FINANCIAL -> List.of("DETERMINISTIC_FINANCIAL_ANALYSIS");
            case CONTRACT -> List.of("CONTRACT_STRUCTURE_CHECK");
            case LEGAL -> List.of("LEGAL_REVIEW_FLAGS");
            case MATCHING -> List.of("BUILDER_MATCHING");
            case RESEARCH -> List.of("DOMAIN_AGENT_AGGREGATION");
        };
    }

    private String provider(AgentType type) {
        return switch (type) {
            case PERSIAN -> "AI_PROVIDER_WITH_RULE_FALLBACK";
            case RESEARCH -> "INTERNAL_ORCHESTRATION";
            default -> "JAVA_DETERMINISTIC";
        };
    }
}
