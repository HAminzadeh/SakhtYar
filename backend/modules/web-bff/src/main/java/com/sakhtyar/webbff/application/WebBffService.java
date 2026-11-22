package com.sakhtyar.webbff.application;

import com.sakhtyar.analysis.feasibility.application.FeasibilityService;
import com.sakhtyar.builder.application.BuilderService;
import com.sakhtyar.casefile.application.CaseService;
import com.sakhtyar.contract.application.ContractService;
import com.sakhtyar.finance.application.FinancialAnalysisService;
import com.sakhtyar.market.application.MarketIntelligenceService;
import com.sakhtyar.property.application.PropertyService;
import com.sakhtyar.property.api.PropertyDtos.PropertyResponse;
import com.sakhtyar.regulation.application.RegulationEvidenceService;
import com.sakhtyar.sensitivity.application.SensitivityService;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WebBffService {
  private final CaseService cases;
  private final PropertyService properties;
  private final RegulationEvidenceService regulations;
  private final FeasibilityService feasibility;
  private final FinancialAnalysisService finance;
  private final SensitivityService sensitivity;
  private final BuilderService builders;
  private final ContractService contracts;
  private final MarketIntelligenceService market;

  public WebBffService(
      CaseService cases,PropertyService properties,RegulationEvidenceService regulations,
      FeasibilityService feasibility,FinancialAnalysisService finance,SensitivityService sensitivity,
      BuilderService builders,ContractService contracts,MarketIntelligenceService market
  ){
    this.cases=cases;this.properties=properties;this.regulations=regulations;
    this.feasibility=feasibility;this.finance=finance;this.sensitivity=sensitivity;
    this.builders=builders;this.contracts=contracts;this.market=market;
  }

  public Map<String,Object> health(){
    return Map.of("status","UP","service","sakhtyar-web-bff","time",Instant.now().toString());
  }

  public Map<String,Object> caseSummary(UUID caseId){
    Map<String,Object> out=new LinkedHashMap<>();
    out.put("generatedAt",Instant.now());
    out.put("case",cases.get(caseId));

    PropertyResponse property=null;
    try{property=properties.get(caseId);}catch(RuntimeException ignored){}
    out.put("property",property);

    out.put("regulationEvidence",safe(()->regulations.evidence(caseId),List.of()));
    out.put("feasibility",safe(()->feasibility.byCase(caseId),List.of()));
    out.put("financial",safe(()->finance.byCase(caseId),List.of()));
    out.put("sensitivity",safe(()->sensitivity.byCase(caseId),List.of()));
    out.put("builders",safe(()->builders.byCase(caseId),List.of()));
    out.put("contracts",safe(()->contracts.byCase(caseId),List.of()));

    if(property!=null&&property.city()!=null){
      PropertyResponse p=property;
      out.put("market",safe(
        ()->market.summary(p.city(),p.district(),p.neighborhood(),p.propertyType(),30),
        null
      ));
    }else{
      out.put("market",null);
    }
    return out;
  }

  public Map<String,Object> publicMarket(
      String city,String district,String neighborhood,String propertyType,int days
  ){
    Map<String,Object> out=new LinkedHashMap<>();
    out.put("generatedAt",Instant.now());
    out.put("summary",market.summary(city,district,neighborhood,propertyType,days));
    out.put("trend",market.trend(city,district,neighborhood,propertyType,days));
    out.put("snapshot",market.latestSnapshot(city,district,neighborhood,propertyType,days).orElse(null));
    return out;
  }

  private <T>T safe(SupplierX<T> s,T fallback){
    try{return s.get();}catch(RuntimeException ex){return fallback;}
  }
  @FunctionalInterface private interface SupplierX<T>{T get();}
}
