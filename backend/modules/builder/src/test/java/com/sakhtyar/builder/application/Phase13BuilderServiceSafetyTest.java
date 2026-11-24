package com.sakhtyar.builder.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import com.sakhtyar.builder.api.BuilderDtos.*;
class Phase13BuilderServiceSafetyTest {
 private final BuilderProfileRepository profiles=mock(BuilderProfileRepository.class);
 private final BuilderService service=new BuilderService(profiles,mock(ProjectBuilderRepository.class),mock(CaseRepository.class),
   mock(CaseAccessService.class),mock(AuditService.class),mock(BuilderAccountBindingRepository.class),
   mock(BuilderVerificationRepository.class),mock(JdbcTemplate.class));
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private void login(String... authorities){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice","n/a",AuthorityUtils.createAuthorityList(authorities)));}
 private BuilderProfileEntity profile(){return new BuilderProfileEntity(UUID.randomUUID(),"Synthetic",null,"12345","R01",
   "09110000000",null,"private@example.invalid",null,null,"Tehran","Private address",true,
   Map.of("capitalCapacity",1000000),Instant.now(),Instant.now());}
 @Test void memberDirectoryMasksPrivateInformation(){
   login("CASE_READ");when(profiles.findByVerifiedTrueOrderByUpdatedAtDesc()).thenReturn(List.of(profile()));
   BuilderResponse v=service.list().get(0);
   assertNull(v.mobile());assertNull(v.nationalIdentifier());assertNull(v.email());assertNull(v.address());assertTrue(v.metadata().isEmpty());
 }
 @Test void adminCanInspectFullInformation(){
   login("CASE_READ","USER_MANAGE","ROLE_ADMIN");when(profiles.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(profile()));
   assertEquals("12345",service.list().get(0).nationalIdentifier());
 }
 @Test void nonAdminCannotCreateProfiles(){
   login("CASE_READ");var r=new UpsertBuilderRequest("Name",null,null,null,null,null,null,null,null,null,null,false,Map.of());
   assertThrows(ResponseStatusException.class,()->service.create(r));verify(profiles,never()).saveAndFlush(any());
 }
 @Test void createCannotSelfVerify(){
   login("USER_MANAGE");var r=new UpsertBuilderRequest("Name",null,null,null,null,null,null,null,null,null,null,true,Map.of());
   assertThrows(ResponseStatusException.class,()->service.create(r));verify(profiles,never()).saveAndFlush(any());
 }
 @Test void legacyLinkCannotCreatePrimaryWithoutAcceptedProposal(){
   login("USER_MANAGE","CASE_WRITE");var req=new LinkBuilderRequest(UUID.randomUUID(),"SELECTED","ACCEPTED",true,null,Map.of());
   assertThrows(ResponseStatusException.class,()->service.link(UUID.randomUUID(),req));
 }
}
