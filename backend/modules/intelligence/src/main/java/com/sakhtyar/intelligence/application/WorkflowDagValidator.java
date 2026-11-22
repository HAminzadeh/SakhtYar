package com.sakhtyar.intelligence.application;
import com.sakhtyar.intelligence.domain.WorkflowDefinition;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class WorkflowDagValidator {
    public void validate(WorkflowDefinition workflow) {
        if(workflow==null || workflow.steps()==null || workflow.steps().isEmpty() || workflow.steps().size()>32)
            throw new IllegalArgumentException("Workflow must have 1..32 steps.");
        Map<String,List<String>> graph=new HashMap<>();
        for (var s: workflow.steps()) {
            if(s==null || s.id()==null || !s.id().matches("[A-Za-z0-9_-]{1,80}") ||
                s.componentId()==null || !s.componentId().matches("[A-Za-z0-9_.-]{1,100}") ||
                s.dependsOn()==null || s.dependsOn().size()>32 || s.dependsOn().stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("Invalid workflow step or dependency.");
            if(graph.put(s.id(), List.copyOf(s.dependsOn()))!=null)
                throw new IllegalArgumentException("Duplicate step: "+s.id());
        }
        Set<String> visiting=new HashSet<>(), done=new HashSet<>();
        for(String n:graph.keySet()) visit(n,graph,visiting,done);
    }
    private void visit(String n, Map<String,List<String>> g, Set<String> visiting, Set<String> done){ if(done.contains(n))return; if(!g.containsKey(n))throw new IllegalArgumentException("Unknown dependency: "+n); if(!visiting.add(n))throw new IllegalArgumentException("Workflow cycle at: "+n); for(String d:g.get(n))visit(d,g,visiting,done); visiting.remove(n); done.add(n); }
}