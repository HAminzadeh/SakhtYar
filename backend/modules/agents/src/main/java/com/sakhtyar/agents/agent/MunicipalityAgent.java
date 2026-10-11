package com.sakhtyar.agents.agent;
import com.sakhtyar.agents.core.*;
import com.sakhtyar.agents.tools.RegulationEvidenceTool;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MunicipalityAgent implements SakhtyarAgent {
  private final RegulationEvidenceTool tool;
  public MunicipalityAgent(RegulationEvidenceTool tool){this.tool=tool;}
  public AgentType type(){return AgentType.MUNICIPALITY;}
  public String displayName(){return "عامل ضوابط شهرداری";}

  public AgentResult execute(AgentRequest request,AgentExecutionContext context){
    if(request.caseId()==null)
      return AgentResult.needsInput(type(),"برای دریافت ضوابط معتبر، شناسه پرونده الزامی است.",Map.of(),List.of("caseId"));

    Map<String,Object> data;
    try{data=tool.execute(request.caseId());}
    catch(RuntimeException ex){
      return AgentResult.needsInput(type(),"منابع تأییدشده مرتبط با پرونده قابل بازیابی نیستند.",Map.of(),List.of("verified municipality evidence"));
    }

    boolean floors=data.get("allowedResidentialFloors")!=null;
    boolean coverage=data.get("coverageRatio")!=null;

    if(!floors||!coverage)
      return new AgentResult(type(),AgentStatus.NEEDS_INPUT,
        "ضوابط تأییدشده کافی برای اظهار نظر درباره طبقات و سطح اشغال وجود ندارد.",data,
        List.of("Client-supplied municipality rules are ignored; only approved published evidence is accepted."),
        List.of(!floors?"approved maxFloors":"approved coverageRatio",
                !coverage?"approved maxCoveragePercent":"approved floors"),
        0.0,Instant.now());

    return new AgentResult(type(),AgentStatus.SUCCESS,
      "مقادیر ضوابط از سرویس Backend و Ruleهای متصل به دانش تأییدشده دریافت شدند؛ این نتیجه مجوز رسمی نیست.",
      data,List.of("Engineering and municipal verification remains required."),List.of(),1.0,Instant.now());
  }
}
