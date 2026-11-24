package com.sakhtyar.builder.application;

import static com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.application.*;
import com.sakhtyar.casefile.domain.*;
import com.sakhtyar.contract.application.ContractService;
import com.sakhtyar.contract.api.ContractDtos.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app invite / proposal workflow. No messages, external calls, legal signature,
 * or account self-registration. All mutations lock the case first for serial decisions.
 */
@Service
public class BuilderEngagementService {
    private final BuilderProfileRepository builders;
    private final BuilderAccountBindingRepository bindings;
    private final BuilderInvitationRepository invites;
    private final BuilderProposalRepository proposals;
    private final BuilderProposalRevisionRepository versions;
    private final ProjectBuilderRepository links;
    private final CaseRepository cases;
    private final CaseAccessService access;
    private final AuditService audit;
    private final ContractService contracts;
    public BuilderEngagementService(BuilderProfileRepository builders,BuilderAccountBindingRepository bindings,
            BuilderInvitationRepository invites,BuilderProposalRepository proposals,
            BuilderProposalRevisionRepository versions,ProjectBuilderRepository links,
            CaseRepository cases,CaseAccessService access,AuditService audit,ContractService contracts){
        this.builders=builders;this.bindings=bindings;this.invites=invites;this.proposals=proposals;
        this.versions=versions;this.links=links;this.cases=cases;this.access=access;this.audit=audit;this.contracts=contracts;
    }
    private CaseEntity locked(UUID caseId){
        CaseEntity c=cases.findByIdForUpdate(caseId).orElseThrow(BuilderPolicy::deny);
        if(!"REAL".equals(c.getDataScope()))throw BuilderPolicy.deny();
        CaseLifecycle.requireEditable(c.getStatus(),c.getStatus());
        return c;
    }
    private void ownerWrite(CaseEntity c){BuilderPolicy.authority("CASE_WRITE");access.requireWrite(c);}
    private void ownerRead(UUID caseId){BuilderPolicy.authority("CASE_WRITE");access.requireWrite(caseId);}
    private void bound(UUID builderId){
        String username=BuilderPolicy.username();
        BuilderAccountBindingEntity b=bindings.findByUsernameIgnoreCase(username).orElseThrow(BuilderPolicy::deny);
        if(!b.getBuilderId().equals(builderId))throw BuilderPolicy.deny();
    }
    private BuilderInvitationEntity invitation(UUID id){return invites.findById(id).orElseThrow(BuilderPolicy::deny);}
    private BuilderProposalEntity proposal(UUID id){return proposals.findById(id).orElseThrow(BuilderPolicy::deny);}
    private void active(BuilderInvitationEntity i){
        if(!"ACCEPTED".equals(i.getStatus()))
            throw BuilderPolicy.conflict("Invitation is not accepted and current.");
    }
    @Transactional
    public InvitationResponse invite(UUID caseId,InvitationRequest req){
        if(req==null || req.builderId()==null || req.validDays()<1 || req.validDays()>90)
            throw BuilderPolicy.invalid("Invitation requires builder and 1..90 days.");
        CaseEntity c=locked(caseId);ownerWrite(c);
        var b=builders.findById(req.builderId()).orElseThrow(BuilderPolicy::deny);
        if(!b.isVerified())throw BuilderPolicy.conflict("Builder requires reviewed profile.");
        if(!bindings.existsById(req.builderId()))throw BuilderPolicy.conflict("Builder account binding required.");
        for(var old:invites.findByCaseIdAndBuilderIdOrderByCreatedAtDesc(caseId,req.builderId())){
            if("PENDING".equals(old.getStatus())){
                if(old.getExpiresAt().isAfter(Instant.now()))throw BuilderPolicy.conflict("An invitation is pending.");
                old.status("EXPIRED");invites.saveAndFlush(old);
            }
            if("ACCEPTED".equals(old.getStatus()))throw BuilderPolicy.conflict("Builder already accepted invitation.");
        }
        Instant now=Instant.now();
        var e=new BuilderInvitationEntity(UUID.randomUUID(),caseId,req.builderId(),"PENDING",
                now.plus(req.validDays(),ChronoUnit.DAYS),BuilderPolicy.username(),now);
        invites.saveAndFlush(e);
        audit.record("BUILDER_INVITATION",e.getId(),"BUILDER_INVITED",Map.of("caseId",caseId.toString()));
        return InvitationResponse.from(e);
    }
    @Transactional(readOnly=true)
    public List<InvitationResponse> invitations(UUID caseId){
        ownerRead(caseId);
        return invites.findByCaseIdOrderByCreatedAtDesc(caseId).stream().map(InvitationResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public List<InvitationResponse> inbox(){
        BuilderPolicy.actor();
        UUID builderId=bindings.findByUsernameIgnoreCase(BuilderPolicy.username())
                .orElseThrow(BuilderPolicy::deny).getBuilderId();
        return invites.findByBuilderIdOrderByCreatedAtDesc(builderId).stream().map(InvitationResponse::from).toList();
    }
    @Transactional
    public InvitationResponse respond(UUID invitationId,boolean accept){
        bound(invites.findBuilderIdById(invitationId).orElseThrow(BuilderPolicy::deny));
        locked(invites.findCaseIdById(invitationId).orElseThrow(BuilderPolicy::deny)); // scalar projection before locking
        BuilderInvitationEntity i=invitation(invitationId);bound(i.getBuilderId());
        if(!builders.findById(i.getBuilderId()).map(BuilderProfileEntity::isVerified).orElse(false))
            throw BuilderPolicy.conflict("Builder verification no longer active.");
        if(!"PENDING".equals(i.getStatus()) || !i.getExpiresAt().isAfter(Instant.now()))
            throw BuilderPolicy.conflict("Invitation no longer pending.");
        i.status(accept?"ACCEPTED":"DECLINED");invites.saveAndFlush(i);
        audit.record("BUILDER_INVITATION",i.getId(),accept?"INVITATION_ACCEPTED":"INVITATION_DECLINED",
                Map.of("caseId",i.getCaseId().toString()));
        return InvitationResponse.from(i);
    }
    @Transactional
    public InvitationResponse cancel(UUID invitationId){
        CaseEntity c=locked(invites.findCaseIdById(invitationId).orElseThrow(BuilderPolicy::deny));ownerWrite(c);
        BuilderInvitationEntity i=invitation(invitationId);
        if(!"PENDING".equals(i.getStatus()))throw BuilderPolicy.conflict("Invitation is not pending.");
        i.status("CANCELLED");invites.saveAndFlush(i);
        audit.record("BUILDER_INVITATION",i.getId(),"INVITATION_CANCELLED",Map.of("caseId",i.getCaseId().toString()));
        return InvitationResponse.from(i);
    }
    @Transactional
    public ProposalResponse submit(UUID invitationId,SubmitProposalRequest r){
        BuilderPolicy.proposal(r);
        bound(invites.findBuilderIdById(invitationId).orElseThrow(BuilderPolicy::deny));
        locked(invites.findCaseIdById(invitationId).orElseThrow(BuilderPolicy::deny));
        BuilderInvitationEntity i=invitation(invitationId);bound(i.getBuilderId());active(i);
        if(!builders.findById(i.getBuilderId()).map(BuilderProfileEntity::isVerified).orElse(false))
            throw BuilderPolicy.conflict("Builder verification no longer active.");
        BuilderProposalEntity p=proposals.findByInvitationId(invitationId).orElse(null);
        Instant now=Instant.now();
        if(p==null){
            p=new BuilderProposalEntity(UUID.randomUUID(),i.getCaseId(),i.getBuilderId(),i.getId(),
                    r.proposedSharePercent(),r.durationMonths(),r.terms(),now);
        }else{
            if(!"CHANGES_REQUESTED".equals(p.getStatus()))throw BuilderPolicy.conflict("Proposal is not editable.");
            if(p.getRevisionNo()>=10000)throw BuilderPolicy.conflict("Proposal revision limit exceeded.");
            p.revise(r.proposedSharePercent(),r.durationMonths(),r.terms());
        }
        proposals.saveAndFlush(p);append(p);
        audit.record("BUILDER_PROPOSAL",p.getId(),"PROPOSAL_SUBMITTED",
                Map.of("caseId",p.getCaseId().toString(),"revision",p.getRevisionNo()));
        return ProposalResponse.from(p);
    }
    private void append(BuilderProposalEntity p){
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("share",p.getProposedSharePercent());snapshot.put("durationMonths",p.getDurationMonths());
        snapshot.put("terms",p.getTerms());snapshot.put("status",p.getStatus());
        versions.saveAndFlush(new BuilderProposalRevisionEntity(UUID.randomUUID(),p.getId(),p.getRevisionNo(),
                snapshot,BuilderPolicy.username(),Instant.now()));
    }
    @Transactional(readOnly=true)
    public ProposalResponse ownProposal(UUID invitationId){
        BuilderInvitationEntity i=invitation(invitationId);bound(i.getBuilderId());
        return ProposalResponse.from(proposals.findByInvitationId(invitationId).orElseThrow(BuilderPolicy::deny));
    }
    @Transactional(readOnly=true)
    public List<ProposalResponse> proposals(UUID caseId){
        ownerRead(caseId);
        return proposals.findByCaseIdOrderByUpdatedAtDesc(caseId).stream().map(ProposalResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public List<ProposalVersionResponse> history(UUID proposalId){
        BuilderProposalEntity p=proposal(proposalId);
        if(!isOwn(p.getBuilderId()))ownerRead(p.getCaseId());
        return versions.findByProposalIdOrderByRevisionNoAsc(proposalId).stream().map(ProposalVersionResponse::from).toList();
    }
    private boolean isOwn(UUID builderId){
        BuilderPolicy.actor();
        return bindings.findByUsernameIgnoreCase(BuilderPolicy.username())
                .map(b->b.getBuilderId().equals(builderId)).orElse(false);
    }
    @Transactional
    public ProposalResponse decide(UUID proposalId,ProposalDecisionRequest r){
        if(r==null)throw BuilderPolicy.invalid("Decision required.");
        String action=BuilderPolicy.normal(r.action());
        if(!Set.of("ACCEPT","REJECT","REQUEST_CHANGES").contains(action))throw BuilderPolicy.invalid("Unknown decision.");
        CaseEntity c=locked(proposals.findCaseIdById(proposalId).orElseThrow(BuilderPolicy::deny));ownerWrite(c);
        BuilderProposalEntity p=proposal(proposalId);
        if(!"SUBMITTED".equals(p.getStatus()))throw BuilderPolicy.conflict("Proposal is not awaiting a decision.");
        if("ACCEPT".equals(action)){
            if(!builders.findById(p.getBuilderId()).map(BuilderProfileEntity::isVerified).orElse(false))
                throw BuilderPolicy.conflict("Builder verification no longer active.");
            if(proposals.existsByCaseIdAndStatus(p.getCaseId(),"ACCEPTED"))throw BuilderPolicy.conflict("Case already selected a proposal.");
            for(var link:links.findByCaseIdOrderByCreatedAtAsc(p.getCaseId())){
                if(link.isPrimary() && !link.getBuilderId().equals(p.getBuilderId()))
                    throw BuilderPolicy.conflict("Case already has a primary builder.");
            }
            var link=links.findByCaseIdAndBuilderId(p.getCaseId(),p.getBuilderId()).orElse(null);
            if(link==null)link=new ProjectBuilderEntity(UUID.randomUUID(),p.getCaseId(),p.getBuilderId(),
                    "SELECTED","ACCEPTED",true,p.getProposedSharePercent(),Map.of("proposalId",p.getId().toString()),
                    Instant.now(),Instant.now());
            else link.update("SELECTED","ACCEPTED",true,p.getProposedSharePercent(),Map.of("proposalId",p.getId().toString()));
            links.saveAndFlush(link);
        }
        p.decide(switch(action){case "ACCEPT"->"ACCEPTED";case "REJECT"->"REJECTED";default->"CHANGES_REQUESTED";});
        proposals.saveAndFlush(p);
        audit.record("BUILDER_PROPOSAL",p.getId(),"PROPOSAL_"+action,
                Map.of("caseId",p.getCaseId().toString(),"revision",p.getRevisionNo()));
        return ProposalResponse.from(p);
    }
    /** Deterministic, unsigned, explicitly unreviewed contract draft from an accepted proposal. */
    @Transactional
    public ContractResponse contractDraft(UUID proposalId){
        CaseEntity c=locked(proposals.findCaseIdById(proposalId).orElseThrow(BuilderPolicy::deny));ownerWrite(c);
        BuilderProposalEntity p=proposal(proposalId);
        if(!"ACCEPTED".equals(p.getStatus()))throw BuilderPolicy.conflict("Only an accepted proposal can seed a draft.");
        String code="PROPOSAL-"+p.getId().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT);
        Map<String,Object> terms=new LinkedHashMap<>();
        terms.put("SHARES",Map.of("proposedBuilderSharePercent",p.getProposedSharePercent()));
        terms.put("DURATION",Map.of("proposedDurationMonths",p.getDurationMonths()));
        terms.put("UNREVIEWED_PROPOSAL_TERMS",p.getTerms());
        Map<String,Object> metadata=Map.of("source","BUILDER_PROPOSAL", "sourceProposalId",p.getId().toString(),
            "sourceProposalRevision",p.getRevisionNo(),"requiresLegalReview",true);
        ContractResponse draft=contracts.create(p.getCaseId(),new UpsertContractRequest(code,
            "PARTICIPATION_IN_CONSTRUCTION","DRAFT","پیش‌نویس بر اساس پیشنهاد سازنده",null,
            null,null,"1.0",terms,metadata),null);
        audit.record("BUILDER_PROPOSAL",p.getId(),"PROPOSAL_DRAFT_CREATED",
                Map.of("contractId",draft.id().toString(),"revision",p.getRevisionNo()));
        return draft;
    }
    @Transactional
    public ProposalResponse withdraw(UUID proposalId){
        bound(proposals.findBuilderIdById(proposalId).orElseThrow(BuilderPolicy::deny));
        locked(proposals.findCaseIdById(proposalId).orElseThrow(BuilderPolicy::deny));
        BuilderProposalEntity p=proposal(proposalId);bound(p.getBuilderId());
        if(!Set.of("SUBMITTED","CHANGES_REQUESTED").contains(p.getStatus()))
            throw BuilderPolicy.conflict("Proposal cannot be withdrawn.");
        p.decide("WITHDRAWN");proposals.saveAndFlush(p);
        audit.record("BUILDER_PROPOSAL",p.getId(),"PROPOSAL_WITHDRAWN",
                Map.of("caseId",p.getCaseId().toString()));
        return ProposalResponse.from(p);
    }
}
