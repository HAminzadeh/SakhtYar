package com.sakhtyar.agents.core;

/**
 * A deterministic tool exposed to agents. Tools should delegate to normal
 * application services instead of duplicating domain logic inside agents.
 */
public interface AgentTool<I, O> {

    String name();

    O execute(I input);
}
