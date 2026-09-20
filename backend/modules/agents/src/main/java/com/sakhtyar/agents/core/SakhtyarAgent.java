package com.sakhtyar.agents.core;

public interface SakhtyarAgent {

    AgentType type();

    default String displayName() {
        return type().name();
    }

    AgentResult execute(AgentRequest request, AgentExecutionContext context);
}
