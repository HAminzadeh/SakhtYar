package com.sakhtyar.agents.tools;
import com.sakhtyar.agents.core.AgentTool;
import com.sakhtyar.finance.application.FinancialAnalysisService;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class FinancialSummaryTool implements AgentTool<UUID,Map<String,Object>>{
  private final FinancialAnalysisService service;
  public FinancialSummaryTool(FinancialAnalysisService service){this.service=service;}
  public String name(){return "financial-summary";}
  public Map<String,Object> execute(UUID caseId){
    var list=service.byCase(caseId);
    if(list.isEmpty())throw new IllegalArgumentException("No persisted financial analysis for case.");
    var x=list.get(0);
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("financialAnalysisId",x.id());m.put("scenarioId",x.scenarioId());m.put("currencyCode",x.currencyCode());
    m.put("grossRevenue",x.grossRevenue());m.put("totalProjectCost",x.totalProjectCost());
    m.put("projectedProfit",x.projectedProfit());m.put("roiPercent",x.roiPercent());
    m.put("profitMarginPercent",x.profitMarginPercent());m.put("calculatedAt",x.calculatedAt());
    m.put("allocations",x.allocations());
    return m;
  }
}
