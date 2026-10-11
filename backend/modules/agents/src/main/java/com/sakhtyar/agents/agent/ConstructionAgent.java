package com.sakhtyar.agents.agent;
import com.sakhtyar.agents.core.*;
import com.sakhtyar.agents.tools.FeasibilitySummaryTool;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ConstructionAgent implements SakhtyarAgent {
  private final FeasibilitySummaryTool tool;
  public ConstructionAgent(FeasibilitySummaryTool tool){this.tool=tool;}
  public AgentType type(){return AgentType.CONSTRUCTION;}
  public String displayName(){return "عامل محاسبات ساخت";}

  public AgentResult execute(AgentRequest request,AgentExecutionContext context){
    if(request.caseId()==null)
      return AgentResult.needsInput(type(),"برای تحلیل ساخت، شناسه پرونده لازم است.",Map.of(),List.of("caseId"));

    try{
      Map<String,Object> data=tool.execute(request.caseId());
      String status=String.valueOf(data.get("status"));
      AgentStatus as="FEASIBLE".equals(status)?AgentStatus.SUCCESS:AgentStatus.PARTIAL;
      return new AgentResult(type(),as,
        "خروجی ساخت از آخرین Feasibility Assessment ذخیره‌شده در Backend خوانده شد.",data,
        List.of("Agent فرمول ساخت موازی اجرا نمی‌کند؛ سناریو، ضوابط و هزینه باید در موتورهای اصلی محاسبه شوند."),
        List.of(),0.98,Instant.now());
    }catch(RuntimeException ex){
      return AgentResult.needsInput(type(),"هنوز امکان‌سنجی نسخه‌دار برای این پرونده وجود ندارد.",Map.of(),
        List.of("scenario","verified urban evaluation","scenario cost snapshot","feasibility assessment"));
    }
  }
}
