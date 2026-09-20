package com.sakhtyar.agents.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentExecutionContext {

    private final AgentBus bus;
    private final Map<AgentType, AgentResult> results;
    private final List<AgentType> callChain;

    private AgentExecutionContext(
            AgentBus bus,
            Map<AgentType, AgentResult> results,
            List<AgentType> callChain
    ) {
        this.bus = bus;
        this.results = results;
        this.callChain = callChain;
    }

    static AgentExecutionContext root(AgentBus bus) {
        return new AgentExecutionContext(
                bus,
                new ConcurrentHashMap<>(),
                List.of()
        );
    }

    AgentExecutionContext enter(AgentType type) {
        ArrayList<AgentType> next = new ArrayList<>(callChain);
        next.add(type);
        return new AgentExecutionContext(bus, results, List.copyOf(next));
    }

    boolean contains(AgentType type) {
        return callChain.contains(type);
    }

    int depth() {
        return callChain.size();
    }

    void remember(AgentType type, AgentResult result) {
        if (type != null && result != null) {
            results.put(type, result);
        }
    }

    public Optional<AgentResult> resultOf(AgentType type) {
        return Optional.ofNullable(results.get(type));
    }

    public AgentResult call(AgentType type, AgentRequest request) {
        return bus.invoke(type, request, this);
    }

    public Map<AgentType, AgentResult> snapshot() {
        return Map.copyOf(results);
    }

    public List<AgentType> callChain() {
        return callChain;
    }
}
