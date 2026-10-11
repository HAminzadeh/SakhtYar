package com.sakhtyar.agents.tools;
import com.sakhtyar.agents.core.AgentTool;
import com.sakhtyar.analysis.feasibility.application.FeasibilityService;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class FeasibilitySummaryTool implements AgentTool<UUID,Map<String,Object>>{
  private final FeasibilityService service;
  public FeasibilitySummaryTool(FeasibilityService service){this.service=service;}
  public String name(){return "feasibility-summary";}
  public Map<String,Object> execute(UUID caseId){
    var list=service.byCase(caseId);
    if(list.isEmpty())throw new IllegalArgumentException("No persisted feasibility assessment for case.");
    var x=list.get(0);
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("assessmentId",x.id());m.put("scenarioId",x.scenarioId());m.put("status",x.status().name());
    m.put("readinessScore",x.readinessScore());m.put("scenarioCost",x.scenarioCost());
    m.put("costCurrencyCode",x.costCurrencyCode());m.put("landAreaM2",x.landAreaM2());
    m.put("totalBuiltAreaM2",x.totalBuiltAreaM2());m.put("costPerBuiltM2",x.costPerBuiltM2());
    m.put("proposedFloors",x.proposedFloors());m.put("calculatedFar",x.calculatedFar());
    m.put("regulationStatus",x.regulationStatus());m.put("assessedAt",x.assessedAt());
    m.put("reasons",x.reasons());
    return m;
  }
}
