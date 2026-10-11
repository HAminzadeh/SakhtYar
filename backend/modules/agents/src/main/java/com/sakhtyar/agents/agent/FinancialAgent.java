package com.sakhtyar.agents.agent;
import com.sakhtyar.agents.core.*;
import com.sakhtyar.agents.tools.FinancialSummaryTool;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FinancialAgent implements SakhtyarAgent {
  private final FinancialSummaryTool tool;
  public FinancialAgent(FinancialSummaryTool tool){this.tool=tool;}
  public AgentType type(){return AgentType.FINANCIAL;}
  public String displayName(){return "عامل تحلیل مالی";}

  public AgentResult execute(AgentRequest request,AgentExecutionContext context){
    if(request.caseId()==null)
      return AgentResult.needsInput(type(),"برای تحلیل مالی سرویس‌محور، شناسه پرونده لازم است.",Map.of(),List.of("caseId"));
    try{
      Map<String,Object> data=tool.execute(request.caseId());
      return new AgentResult(type(),AgentStatus.SUCCESS,
        "آخرین تحلیل مالی نسخه‌دار از FinancialAnalysisService خوانده شد.",data,
        List.of("Agent دیگر محاسبه مالی موازی انجام نمی‌دهد؛ اعداد از موتور مالی Backend می‌آیند."),
        List.of(),0.98,Instant.now());
    }catch(RuntimeException ex){
      return AgentResult.needsInput(type(),"برای این پرونده هنوز تحلیل مالی معتبر ذخیره نشده است.",Map.of(),
        List.of("feasibility assessment","explicit sale assumptions","financial analysis"));
    }
  }
}
