package com.sakhtyar.builder.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.*;
import com.sakhtyar.contract.application.ContractService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class Phase13BuilderEngagementTest {
 private BuilderProfileRepository profiles;
 private BuilderAccountBindingRepository bindings;
 private BuilderInvitationRepository invites;
 private BuilderProposalRepository proposals;
 private BuilderProposalRevisionRepository versions;
 private ProjectBuilderRepository links;
 private CaseRepository cases;
 private CaseAccessService access;
 private AuditService audit;
 private ContractService contracts;
 private BuilderEngagementService service;
 private final UUID caseId=UUID.randomUUID(),builderId=UUID.randomUUID();
 private CaseEntity current;
 @BeforeEach void init(){
   profiles=mock(BuilderProfileRepository.class);bindings=mock(BuilderAccountBindingRepository.class);
   invites=mock(BuilderInvitationRepository.class);proposals=mock(BuilderProposalRepository.class);
   versions=mock(BuilderProposalRevisionRepository.class);links=mock(ProjectBuilderRepository.class);
   cases=mock(CaseRepository.class);access=mock(CaseAccessService.class);audit=mock(AuditService.class);
   contracts=mock(ContractService.class);
   service=new BuilderEngagementService(profiles,bindings,invites,proposals,versions,links,cases,access,audit,contracts);
   current=new CaseEntity(caseId,"C-123","Synthetic",CaseStatus.DRAFT,null,null,null,null,null,null,
       "owner",Instant.now(),Instant.now());
   ReflectionTestUtils.setField(current,"dataScope","REAL");
   when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(current));
   when(bindings.findByUsernameIgnoreCase("builder-user")).thenReturn(Optional.of(new BuilderAccountBindingEntity(
       builderId,"builder-user","operator-reviewed","admin",Instant.now())));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private void login(String username,String... authorities){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
     username,"n/a",AuthorityUtils.createAuthorityList(authorities)));}
 private BuilderProfileEntity verifiedBuilder(){return new BuilderProfileEntity(builderId,"Synthetic",null,null,null,null,
    null,null,null,null,"Tehran",null,true,Map.of(),Instant.now(),Instant.now());}
 private BuilderInvitationEntity accepted(){return new BuilderInvitationEntity(UUID.randomUUID(),caseId,builderId,
    "ACCEPTED",Instant.now().plusSeconds(86400),"owner",Instant.now());}
 @Test void outsiderCannotInvite(){
   login("outsider","CASE_READ");assertThrows(ResponseStatusException.class,()->service.invite(caseId,new InvitationRequest(builderId,14)));
   verifyNoInteractions(invites,profiles);
 }
 @Test void ownerCannotInviteUnverifiedBuilder(){
   login("owner","CASE_WRITE");var b=new BuilderProfileEntity(builderId,"Synthetic",null,null,null,null,null,null,null,
       null,"Tehran",null,false,Map.of(),Instant.now(),Instant.now());
   when(profiles.findById(builderId)).thenReturn(Optional.of(b));
   assertThrows(ResponseStatusException.class,()->service.invite(caseId,new InvitationRequest(builderId,14)));
   verify(invites,never()).saveAndFlush(any());
 }
 @Test void randomUserCannotAcceptSomeoneElsesInvite(){
   login("wrong-user","CASE_READ");UUID id=UUID.randomUUID();
   when(invites.findBuilderIdById(id)).thenReturn(Optional.of(builderId));
   assertThrows(ResponseStatusException.class,()->service.respond(id,true));
   verifyNoInteractions(cases);
 }
 @Test void boundBuilderCanSubmitSyntheticOffer(){
   login("builder-user","CASE_READ");BuilderInvitationEntity inv=accepted();
   when(profiles.findById(builderId)).thenReturn(Optional.of(verifiedBuilder()));
   when(invites.findBuilderIdById(inv.getId())).thenReturn(Optional.of(builderId));
   when(invites.findCaseIdById(inv.getId())).thenReturn(Optional.of(caseId));
   when(invites.findById(inv.getId())).thenReturn(Optional.of(inv));
   when(proposals.findByInvitationId(inv.getId())).thenReturn(Optional.empty());
   var response=service.submit(inv.getId(),new SubmitProposalRequest(new BigDecimal("45.0000"),18,Map.of("notes","synthetic")));
   assertEquals("SUBMITTED",response.status());assertEquals(1,response.revisionNo());
   verify(versions).saveAndFlush(any(BuilderProposalRevisionEntity.class));
   verify(audit).record(eq("BUILDER_PROPOSAL"),any(UUID.class),eq("PROPOSAL_SUBMITTED"),any());
 }
 @Test void ownerCannotAcceptSecondAlreadyAcceptedProposal(){
   login("owner","CASE_WRITE");when(profiles.findById(builderId)).thenReturn(Optional.of(verifiedBuilder()));
   var p=new BuilderProposalEntity(UUID.randomUUID(),caseId,builderId,UUID.randomUUID(),
       BigDecimal.TEN,12,Map.of(),Instant.now());
   when(proposals.findCaseIdById(p.getId())).thenReturn(Optional.of(caseId));
   when(proposals.findById(p.getId())).thenReturn(Optional.of(p));
   when(proposals.existsByCaseIdAndStatus(caseId,"ACCEPTED")).thenReturn(true);
   assertThrows(ResponseStatusException.class,()->service.decide(p.getId(),new ProposalDecisionRequest("ACCEPT")));
   verify(links,never()).saveAndFlush(any());
 }
 @Test void ownerSelectionCreatesPrimaryFromAcceptedOfferOnly(){
   login("owner","CASE_WRITE");when(profiles.findById(builderId)).thenReturn(Optional.of(verifiedBuilder()));
   var p=new BuilderProposalEntity(UUID.randomUUID(),caseId,builderId,UUID.randomUUID(),
       new BigDecimal("40"),20,Map.of(),Instant.now());
   when(proposals.findCaseIdById(p.getId())).thenReturn(Optional.of(caseId));
   when(proposals.findById(p.getId())).thenReturn(Optional.of(p));
   when(links.findByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());
   assertEquals("ACCEPTED",service.decide(p.getId(),new ProposalDecisionRequest("ACCEPT")).status());
   verify(links).saveAndFlush(argThat(link->link.isPrimary() && "SELECTED".equals(link.getRole())));
 }
 @Test void unacceptedProposalCannotCreateContractDraft(){
   login("owner","CASE_WRITE");var p=new BuilderProposalEntity(UUID.randomUUID(),caseId,builderId,UUID.randomUUID(),
      new BigDecimal("40"),20,Map.of(),Instant.now());
   when(proposals.findCaseIdById(p.getId())).thenReturn(Optional.of(caseId));
   when(proposals.findById(p.getId())).thenReturn(Optional.of(p));
   assertThrows(ResponseStatusException.class,()->service.contractDraft(p.getId()));
   verifyNoInteractions(contracts);
 }

}
