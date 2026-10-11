package com.sakhtyar.intelligence.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class IntelligenceDtos{
  private IntelligenceDtos(){}

  public record StepRequest(
      @NotBlank String id,
      @NotBlank String componentId,
      List<String> dependsOn
  ){}

  public record ExecuteWorkflowRequest(
      @NotBlank String workflowId,
      @NotBlank String version,
      @NotEmpty List<@Valid StepRequest> steps,
      String inputArtifactId,
      Map<String,Object> config
  ){}

  public record StepResult(
      String stepId,String componentId,String status,String outputArtifactId,
      Map<String,Object> metrics,String error
  ){}

  public record ExecutionResponse(
      String executionId,String workflowId,String version,String status,
      Instant startedAt,Instant completedAt,List<StepResult> steps
  ){}
}
