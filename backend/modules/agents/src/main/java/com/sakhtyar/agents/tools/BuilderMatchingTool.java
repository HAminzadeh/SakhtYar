package com.sakhtyar.agents.tools;
import com.sakhtyar.agents.core.AgentTool;
import com.sakhtyar.builder.api.BuilderDtos.MatchResponse;
import com.sakhtyar.builder.application.BuilderMatchingService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class BuilderMatchingTool implements AgentTool<BuilderMatchingTool.Input,MatchResponse>{
  public record Input(UUID caseId,BigDecimal requiredCapital,int limit){}
  private final BuilderMatchingService service;
  public BuilderMatchingTool(BuilderMatchingService service){this.service=service;}
  public String name(){return "builder-matching";}
  public MatchResponse execute(Input input){return service.match(input.caseId(),input.requiredCapital(),input.limit());}
}
