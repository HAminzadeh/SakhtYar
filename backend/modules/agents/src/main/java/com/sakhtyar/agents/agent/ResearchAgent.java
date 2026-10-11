package com.sakhtyar.agents.agent;
import com.sakhtyar.agents.core.*;
import com.sakhtyar.agents.support.AgentValues;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ResearchAgent implements SakhtyarAgent {
  public AgentType type(){return AgentType.RESEARCH;}
  public String displayName(){return "عامل تحقیق";}

  public AgentResult execute(AgentRequest request,AgentExecutionContext context){
    List<String> domains=AgentValues.stringList(request.parameters().get("researchDomains"));
    if(domains.isEmpty())domains=List.of("PROPERTY","MUNICIPALITY","VALUATION");

    Map<String,Object> collected=new LinkedHashMap<>();
    List<String>warnings=new ArrayList<>();
    boolean partial=false;

    for(String d:domains){
      AgentType t=toType(d);
      if(t==null||t==AgentType.RESEARCH||t==AgentType.PERSIAN){
        warnings.add("دامنه ناشناخته: "+d);partial=true;continue;
      }
      AgentResult r=context.resultOf(t).orElseGet(()->context.call(t,request));
      collected.put(t.name(),Map.of(
        "status",r.status().name(),
        "data",r.data(),
        "warnings",r.warnings(),
        "missingFields",r.missingFields()
      ));
      if(r.status()!=AgentStatus.SUCCESS)partial=true;
    }

    Map<String,Object> data=new LinkedHashMap<>();
    data.put("requestedDomains",domains);
    data.put("collected",collected);
    data.put("sourcePolicy","BACKEND_SERVICES_ONLY");

    return new AgentResult(type(),partial?AgentStatus.PARTIAL:AgentStatus.SUCCESS,
      "نتایج از Agentهای تخصصی متصل به سرویس‌های Backend تجمیع شد.",
      data,warnings,List.of(),partial?0.75:0.92,Instant.now());
  }

  private AgentType toType(String d){
    if(d==null)return null;
    try{return AgentType.valueOf(d.trim().toUpperCase(Locale.ROOT));}
    catch(Exception e){
      return switch(d.trim().toLowerCase(Locale.ROOT)){
        case "ملک"->AgentType.PROPERTY;
        case "شهرداری","ضوابط"->AgentType.MUNICIPALITY;
        case "قیمت","ارزش"->AgentType.VALUATION;
        case "ساخت"->AgentType.CONSTRUCTION;
        case "مالی"->AgentType.FINANCIAL;
        case "قرارداد"->AgentType.CONTRACT;
        case "حقوقی"->AgentType.LEGAL;
        case "سازنده","تطبیق"->AgentType.MATCHING;
        default->null;
      };
    }
  }
}
