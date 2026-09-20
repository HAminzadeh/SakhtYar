package com.sakhtyar.agents.core;

public interface SakhtyarAgent {

    AgentType type();

    default String displayName() {
        return type().name();
    }

    default String version() {
        return "1.0.0";
    }

    AgentResult execute(AgentRequest request, AgentExecutionContext context);
}
