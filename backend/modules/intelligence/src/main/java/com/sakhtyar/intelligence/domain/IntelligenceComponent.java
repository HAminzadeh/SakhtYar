package com.sakhtyar.intelligence.domain;
import java.util.Map;
public interface IntelligenceComponent {
    ComponentDescriptor descriptor();
    ComponentResult execute(ComponentContext context) throws Exception;
    record ComponentDescriptor(String id, String version, String inputType, String outputType, boolean aiRequired) {}
    record ComponentContext(String executionId, String inputArtifactId, Map<String,Object> config) {}
    record ComponentResult(String outputArtifactId, Map<String,Object> metrics) {}
}