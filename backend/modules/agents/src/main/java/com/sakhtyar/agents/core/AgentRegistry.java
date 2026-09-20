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
}
