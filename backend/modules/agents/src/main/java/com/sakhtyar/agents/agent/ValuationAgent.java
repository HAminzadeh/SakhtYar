package com.sakhtyar.agents.agent;
import com.sakhtyar.agents.core.*;
import com.sakhtyar.agents.tools.MarketValuationTool;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ValuationAgent implements SakhtyarAgent {
  private final MarketValuationTool tool;
  public ValuationAgent(MarketValuationTool tool){this.tool=tool;}
  public AgentType type(){return AgentType.VALUATION;}
  public String displayName(){return "عامل قیمت‌گذاری";}

  public AgentResult execute(AgentRequest request,AgentExecutionContext context){
    if(request.caseId()==null)
      return AgentResult.needsInput(type(),"برای قیمت‌گذاری مبتنی بر بازار، شناسه پرونده لازم است.",Map.of(),List.of("caseId"));

    try{
      Map<String,Object> data=tool.execute(request.caseId());
      long count=((Number)data.getOrDefault("sampleCount",0)).longValue();

      if(count<1||data.get("medianPricePerM2")==null)
        return new AgentResult(type(),AgentStatus.NEEDS_INPUT,
          "داده بازار کافی برای قیمت‌گذاری این ملک وجود ندارد.",data,
          List.of("Agent قیمت بازار را بدون مشاهده Backend حدس نمی‌زند."),
          List.of("market observations / comparables"),0.0,Instant.now());

      boolean value=data.get("estimatedLandValue")!=null;
      return new AgentResult(type(),value?AgentStatus.SUCCESS:AgentStatus.PARTIAL,
        "قیمت‌گذاری از Market Intelligence Backend و املاک مقایسه‌ای انجام شد.",data,
        List.of("این خروجی برآورد تحلیلی است و جایگزین ارزیابی رسمی کارشناس نیست."),
        value?List.of():List.of("landAreaM2"),
        Math.min(0.95,0.55+Math.min(count,20)*0.02),Instant.now());
    }catch(RuntimeException ex){
      return AgentResult.needsInput(type(),"Market Intelligence برای این پرونده داده قابل استفاده ندارد.",
        Map.of(),List.of(ex.getMessage()==null?"market data":ex.getMessage()));
    }
  }
}
