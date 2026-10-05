package com.sakhtyar.intelligence.domain;
import java.util.List;
public record WorkflowDefinition(String id, String version, List<Step> steps) {
    public record Step(String id, String componentId, List<String> dependsOn) {}
}