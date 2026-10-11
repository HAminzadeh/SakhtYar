package com.sakhtyar.agents.tools;

import com.sakhtyar.agents.core.AgentTool;
import com.sakhtyar.market.application.MarketIntelligenceService;
import com.sakhtyar.property.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MarketValuationTool implements AgentTool<UUID,Map<String,Object>>{
  private final PropertyRepository properties;
  private final MarketIntelligenceService market;

  public MarketValuationTool(PropertyRepository properties,MarketIntelligenceService market){
    this.properties=properties;this.market=market;
  }

  public String name(){return "market-valuation";}

  public Map<String,Object> execute(UUID caseId){
    PropertyEntity p=properties.findByCaseId(caseId)
      .orElseThrow(()->new IllegalArgumentException("Property for case not found."));
    if(p.getCity()==null)throw new IllegalArgumentException("Property city is required for market valuation.");

    var summary=market.summary(p.getCity(),p.getDistrict(),p.getNeighborhood(),p.getPropertyType(),30);
    var comps=market.comparables(p.getCity(),p.getDistrict(),p.getNeighborhood(),p.getPropertyType(),p.getLandAreaM2(),20);

    Map<String,Object> result=new LinkedHashMap<>();
    result.put("sampleCount",summary.sampleCount());
    result.put("freshnessStatus",summary.freshnessStatus());
    result.put("medianPricePerM2",summary.medianPricePerM2());
    result.put("averagePricePerM2",summary.averagePricePerM2());
    result.put("changePercent30d",summary.changePercent());
    result.put("currencyCode",summary.currencyCode());
    result.put("latestObservedAt",summary.latestObservedAt());
    result.put("comparableCount",comps.size());
    result.put("comparables",comps);
    result.put("landAreaM2",p.getLandAreaM2());
    result.put("city",p.getCity());
    result.put("district",p.getDistrict());

    if(p.getLandAreaM2()!=null&&summary.medianPricePerM2()!=null)
      result.put("estimatedLandValue",summary.medianPricePerM2().multiply(p.getLandAreaM2()));
    return result;
  }
}
