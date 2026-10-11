package com.sakhtyar.intelligence.application;

import static com.sakhtyar.intelligence.api.IntelligenceDtos.*;
import com.sakhtyar.intelligence.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkflowExecutor {
  private final ComponentRegistry registry;
  private final WorkflowDagValidator validator;
  private final JdbcTemplate jdbc;

  public WorkflowExecutor(ComponentRegistry registry,WorkflowDagValidator validator,JdbcTemplate jdbc){
    this.registry=registry;this.validator=validator;this.jdbc=jdbc;
  }

  public ExecutionResponse execute(ExecuteWorkflowRequest r){
    WorkflowDefinition workflow=new WorkflowDefinition(
      r.workflowId(),r.version(),
      r.steps().stream()
        .map(x->new WorkflowDefinition.Step(
          x.id(),x.componentId(),x.dependsOn()==null?List.of():List.copyOf(x.dependsOn())
        )).toList()
    );
    validator.validate(workflow);

    UUID executionUuid=UUID.randomUUID();
    String executionId=executionUuid.toString();
    Instant started=Instant.now();

    jdbc.update("""
      insert into intelligence_workflow_execution(
        id,workflow_id,workflow_version,status,input_artifact_id,started_at
      ) values (?,?,?,?,?,?)
      """,executionUuid,workflow.id(),workflow.version(),"RUNNING",r.inputArtifactId(),java.sql.Timestamp.from(started));

    Map<String,String> outputs=new HashMap<>();
    List<StepResult> results=new ArrayList<>();

    try{
      for(var step:topological(workflow)){
        var component=registry.find(step.componentId())
          .orElseThrow(()->new IllegalArgumentException("Unknown intelligence component: "+step.componentId()));

        Map<String,Object> config=new LinkedHashMap<>(r.config()==null?Map.of():r.config());
        Map<String,String> deps=new LinkedHashMap<>();
        for(String d:step.dependsOn())if(outputs.get(d)!=null)deps.put(d,outputs.get(d));
        config.put("dependencyOutputs",deps);

        String input=step.dependsOn().isEmpty()
          ?r.inputArtifactId()
          :outputs.get(step.dependsOn().get(step.dependsOn().size()-1));

        Instant stepStarted=Instant.now();

        try{
          var result=component.execute(
            new IntelligenceComponent.ComponentContext(executionId,input,config)
          );
          outputs.put(step.id(),result.outputArtifactId());

          jdbc.update("""
            insert into intelligence_workflow_step(
              id,execution_id,step_id,component_id,status,input_artifact_id,
              output_artifact_id,started_at,completed_at
            ) values (?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),executionUuid,step.id(),step.componentId(),"COMPLETED",
            input,result.outputArtifactId(),java.sql.Timestamp.from(stepStarted),java.sql.Timestamp.from(Instant.now()));

          results.add(new StepResult(
            step.id(),step.componentId(),"COMPLETED",result.outputArtifactId(),
            result.metrics()==null?Map.of():result.metrics(),null
          ));
        }catch(Exception ex){
          jdbc.update("""
            insert into intelligence_workflow_step(
              id,execution_id,step_id,component_id,status,input_artifact_id,
              error_message,started_at,completed_at
            ) values (?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),executionUuid,step.id(),step.componentId(),"FAILED",
            input,trim(ex.getMessage(),2000),java.sql.Timestamp.from(stepStarted),java.sql.Timestamp.from(Instant.now()));

          results.add(new StepResult(
            step.id(),step.componentId(),"FAILED",null,Map.of(),ex.getMessage()
          ));
          throw ex;
        }
      }

      Instant done=Instant.now();
      jdbc.update("""
        update intelligence_workflow_execution
           set status='COMPLETED',completed_at=?
         where id=?
        """,java.sql.Timestamp.from(done),executionUuid);

      return new ExecutionResponse(
        executionId,workflow.id(),workflow.version(),"COMPLETED",started,done,results
      );
    }catch(Exception ex){
      Instant done=Instant.now();
      jdbc.update("""
        update intelligence_workflow_execution
           set status='FAILED',completed_at=?,error_message=?
         where id=?
        """,java.sql.Timestamp.from(done),trim(ex.getMessage(),2000),executionUuid);

      return new ExecutionResponse(
        executionId,workflow.id(),workflow.version(),"FAILED",started,done,results
      );
    }
  }

  private List<WorkflowDefinition.Step> topological(WorkflowDefinition w){
    List<WorkflowDefinition.Step> out=new ArrayList<>();
    Set<String> done=new HashSet<>();

    while(out.size()<w.steps().size()){
      boolean progressed=false;
      for(var s:w.steps()){
        if(!done.contains(s.id())&&done.containsAll(s.dependsOn())){
          out.add(s);done.add(s.id());progressed=true;
        }
      }
      if(!progressed)throw new IllegalArgumentException("Workflow cannot be topologically ordered.");
    }
    return out;
  }

  private String trim(String s,int max){
    if(s==null)return null;
    return s.length()<=max?s:s.substring(0,max);
  }
}
