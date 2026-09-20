package com.sakhtyar.agents.core;

public enum AgentIntent {
    PROPERTY_ANALYSIS,
    PARTNERSHIP_ANALYSIS,
    PROPERTY_VALUATION,
    BUILDABILITY_ANALYSIS,
    CONTRACT_REVIEW,
    BUILDER_MATCHING,
    RESEARCH;

    public static AgentIntent from(String value) {
        if (value == null || value.isBlank()) {
            return PROPERTY_ANALYSIS;
        }
        try {
            return AgentIntent.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PROPERTY_ANALYSIS;
        }
    }
}
