package com.sakhtyar.intelligence.application;
import com.sakhtyar.intelligence.domain.WorkflowDefinition;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class WorkflowDagValidator {
    public void validate(WorkflowDefinition workflow) {
        Map<String,List<String>> graph=new HashMap<>();
        for (var s: workflow.steps()) { if(graph.put(s.id(), s.dependsOn())!=null) throw new IllegalArgumentException("Duplicate step: "+s.id()); }
        Set<String> visiting=new HashSet<>(), done=new HashSet<>();
        for(String n:graph.keySet()) visit(n,graph,visiting,done);
    }
    private void visit(String n, Map<String,List<String>> g, Set<String> visiting, Set<String> done){ if(done.contains(n))return; if(!g.containsKey(n))throw new IllegalArgumentException("Unknown dependency: "+n); if(!visiting.add(n))throw new IllegalArgumentException("Workflow cycle at: "+n); for(String d:g.get(n))visit(d,g,visiting,done); visiting.remove(n); done.add(n); }
}