package com.sakhtyar.intelligence.api;

import static com.sakhtyar.intelligence.api.IntelligenceDtos.*;
import com.sakhtyar.intelligence.application.*;
import jakarta.validation.Valid;
import java.util.Collection;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/intelligence")
public class IntelligenceController{
  private final ComponentRegistry registry;
  private final WorkflowExecutor executor;

  public IntelligenceController(ComponentRegistry registry,WorkflowExecutor executor){
    this.registry=registry;this.executor=executor;
  }

  @GetMapping("/components")
  public Collection<?> components(){return registry.descriptors();}

  @PostMapping("/workflows/execute")
  public ExecutionResponse execute(@Valid @RequestBody ExecuteWorkflowRequest r){
    return executor.execute(r);
  }
}
