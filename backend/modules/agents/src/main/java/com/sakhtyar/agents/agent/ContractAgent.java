package com.sakhtyar.agents.agent;
import com.sakhtyar.agents.core.*;
import com.sakhtyar.agents.tools.ContractReviewTool;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ContractAgent implements SakhtyarAgent {
  private final ContractReviewTool tool;
  public ContractAgent(ContractReviewTool tool){this.tool=tool;}
  public AgentType type(){return AgentType.CONTRACT;}
  public String displayName(){return "عامل قرارداد";}

  public AgentResult execute(AgentRequest request,AgentExecutionContext context){
    if(request.caseId()==null)
      return AgentResult.needsInput(type(),"برای بررسی قرارداد ثبت‌شده، شناسه پرونده لازم است.",Map.of(),List.of("caseId"));

    try{
      var r=tool.execute(request.caseId());
      Map<String,Object> data=new LinkedHashMap<>();
      data.put("contractId",r.contractId());data.put("contractCode",r.contractCode());
      data.put("contractStatus",r.status());data.put("presentClauses",r.presentClauses());
      data.put("missingClauses",r.missingClauses());data.put("completeness",r.completeness());

      return new AgentResult(type(),r.missingClauses().isEmpty()?AgentStatus.SUCCESS:AgentStatus.PARTIAL,
        r.missingClauses().isEmpty()?"ساختار اصلی قرارداد ثبت‌شده کامل است.":"برخی بندهای کلیدی قرارداد ثبت‌شده نیازمند تکمیل است.",
        data,List.of(r.disclaimer()),r.missingClauses(),0.9,Instant.now());
    }catch(RuntimeException ex){
      return AgentResult.needsInput(type(),"قرارداد ثبت‌شده قابل بررسی برای پرونده وجود ندارد.",
        Map.of(),List.of("persisted contract"));
    }
  }
}
