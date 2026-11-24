package com.sakhtyar.builder.api;

import static com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.builder.application.*;
import com.sakhtyar.contract.api.ContractDtos.ContractResponse;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Separate authenticated route: legacy /api/v1/builders authority matchers stay intact. */
@RestController @RequestMapping("/api/v1/builder-workflow")
public class BuilderWorkflowController {
    private final BuilderService builders;
    private final BuilderEngagementService workflow;
    public BuilderWorkflowController(BuilderService builders,BuilderEngagementService workflow){
        this.builders=builders;this.workflow=workflow;
    }
    @PostMapping("/admin/bind") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bind(@Valid @RequestBody AccountBindingRequest r){builders.bind(r);}
    @PostMapping("/admin/review") public BuilderResponse review(@Valid @RequestBody VerifyBuilderRequest r){return builders.review(r);}
    @PostMapping("/cases/{caseId}/invitations") public InvitationResponse invite(@PathVariable UUID caseId,@Valid @RequestBody InvitationRequest r){return workflow.invite(caseId,r);}
    @GetMapping("/cases/{caseId}/invitations") public List<InvitationResponse> invitations(@PathVariable UUID caseId){return workflow.invitations(caseId);}
    @GetMapping("/inbox") public List<InvitationResponse> inbox(){return workflow.inbox();}
    @PostMapping("/invitations/{id}/accept") public InvitationResponse accept(@PathVariable UUID id){return workflow.respond(id,true);}
    @PostMapping("/invitations/{id}/decline") public InvitationResponse decline(@PathVariable UUID id){return workflow.respond(id,false);}
    @PostMapping("/invitations/{id}/cancel") public InvitationResponse cancel(@PathVariable UUID id){return workflow.cancel(id);}
    @PostMapping("/invitations/{id}/proposal") public ProposalResponse submit(@PathVariable UUID id,@Valid @RequestBody SubmitProposalRequest r){return workflow.submit(id,r);}
    @GetMapping("/invitations/{id}/proposal") public ProposalResponse ownProposal(@PathVariable UUID id){return workflow.ownProposal(id);}
    @GetMapping("/cases/{caseId}/proposals") public List<ProposalResponse> proposals(@PathVariable UUID caseId){return workflow.proposals(caseId);}
    @GetMapping("/proposals/{id}/versions") public List<ProposalVersionResponse> versions(@PathVariable UUID id){return workflow.history(id);}
    @PostMapping("/proposals/{id}/decision") public ProposalResponse decide(@PathVariable UUID id,@Valid @RequestBody ProposalDecisionRequest r){return workflow.decide(id,r);}
    @PostMapping("/proposals/{id}/contract-draft") public ContractResponse draft(@PathVariable UUID id){return workflow.contractDraft(id);}
    @PostMapping("/proposals/{id}/withdraw") public ProposalResponse withdraw(@PathVariable UUID id){return workflow.withdraw(id);}
}
