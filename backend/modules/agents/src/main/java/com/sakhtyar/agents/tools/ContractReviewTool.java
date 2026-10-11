package com.sakhtyar.agents.tools;
import com.sakhtyar.agents.core.AgentTool;
import com.sakhtyar.contract.api.ContractDtos.ContractReviewResponse;
import com.sakhtyar.contract.application.ContractService;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class ContractReviewTool implements AgentTool<UUID,ContractReviewResponse>{
  private final ContractService service;
  public ContractReviewTool(ContractService service){this.service=service;}
  public String name(){return "contract-review";}
  public ContractReviewResponse execute(UUID caseId){return service.review(caseId);}
}
