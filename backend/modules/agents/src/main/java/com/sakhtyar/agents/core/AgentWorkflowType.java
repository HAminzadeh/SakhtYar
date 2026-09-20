package com.sakhtyar.agents.core;

public enum AgentWorkflowType {
    PROPERTY_ANALYSIS,
    PARTNERSHIP_ANALYSIS,
    PROPERTY_VALUATION,
    BUILDABILITY_ANALYSIS,
    CONTRACT_REVIEW,
    BUILDER_MATCHING,
    RESEARCH;

    public static AgentWorkflowType fromIntent(AgentIntent intent) {
        return switch (intent) {
            case PARTNERSHIP_ANALYSIS -> PARTNERSHIP_ANALYSIS;
            case PROPERTY_VALUATION -> PROPERTY_VALUATION;
            case BUILDABILITY_ANALYSIS -> BUILDABILITY_ANALYSIS;
            case CONTRACT_REVIEW -> CONTRACT_REVIEW;
            case BUILDER_MATCHING -> BUILDER_MATCHING;
            case RESEARCH -> RESEARCH;
            case PROPERTY_ANALYSIS -> PROPERTY_ANALYSIS;
        };
    }
}
