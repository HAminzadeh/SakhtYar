package com.sakhtyar.agents.tools;
import com.sakhtyar.agents.core.AgentTool;
import com.sakhtyar.regulation.application.RegulationEvidenceService;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class RegulationEvidenceTool implements AgentTool<UUID,Map<String,Object>>{
  private final RegulationEvidenceService service;
  public RegulationEvidenceTool(RegulationEvidenceService service){this.service=service;}
  public String name(){return "regulation-evidence";}
  public Map<String,Object> execute(UUID caseId){return service.municipalityInputs(caseId);}
}
