package com.sakhtyar.agents.agent;
import com.sakhtyar.agents.core.*;
import com.sakhtyar.agents.support.AgentValues;
import com.sakhtyar.agents.tools.BuilderMatchingTool;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MatchingAgent implements SakhtyarAgent {
  private final BuilderMatchingTool tool;
  public MatchingAgent(BuilderMatchingTool tool){this.tool=tool;}
  public AgentType type(){return AgentType.MATCHING;}
  public String displayName(){return "عامل تطبیق مالک و سازنده";}

  public AgentResult execute(AgentRequest request,AgentExecutionContext context){
    if(request.caseId()==null)
      return AgentResult.needsInput(type(),"برای تطبیق سازنده، شناسه پرونده لازم است.",Map.of(),List.of("caseId"));

    BigDecimal required=AgentValues.decimal(request.parameters(),"requiredCapital");
    try{
      var r=tool.execute(new BuilderMatchingTool.Input(request.caseId(),required,10));
      Map<String,Object> data=new LinkedHashMap<>();
      data.put("matches",r.matches());data.put("candidateCount",r.candidateCount());
      data.put("targetDistrict",r.district());data.put("projectAreaM2",r.projectAreaM2());
      if(required!=null)data.put("requiredCapital",required);

      return new AgentResult(type(),r.candidateCount()==0?AgentStatus.PARTIAL:AgentStatus.SUCCESS,
        r.candidateCount()==0?"سازنده تأییدشده‌ای برای تطبیق پیدا نشد.":"سازندگان از دایرکتوری Backend و بر اساس معیارهای قابل توضیح رتبه‌بندی شدند.",
        data,List.of("امتیاز تطبیق ابزار غربال‌گری است و جایگزین اعتبارسنجی فنی، مالی و حقوقی نیست."),
        List.of(),r.candidateCount()==0?0.3:0.9,Instant.now());
    }catch(RuntimeException ex){
      return AgentResult.needsInput(type(),"اطلاعات Backend برای تطبیق سازنده کامل نیست.",Map.of(),
        List.of(ex.getMessage()==null?"builder data":ex.getMessage()));
    }
  }
}
