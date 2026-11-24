package com.sakhtyar.builder.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.property.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class Phase13BuilderMatchingTest {
 private final BuilderProfileRepository builders=mock(BuilderProfileRepository.class);
 private final PropertyRepository properties=mock(PropertyRepository.class);
 private final CaseAccessService access=mock(CaseAccessService.class);
 private final BuilderMatchingService service=new BuilderMatchingService(builders,properties,access);
 private final UUID caseId=UUID.randomUUID();
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private void login(String... authorities){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice","n/a",AuthorityUtils.createAuthorityList(authorities)));}
 private BuilderProfileEntity profile(UUID id,Map<String,Object> m){
   return new BuilderProfileEntity(id,"Synthetic Builder",null,null,null,null,null,null,null,null,"Tehran",null,true,m,Instant.now(),Instant.now());
 }
 private PropertyEntity property(){
   PropertyEntity p=mock(PropertyEntity.class);
   when(p.getCity()).thenReturn("Tehran");
   when(p.getDistrict()).thenReturn("District 2");
   when(p.getLandAreaM2()).thenReturn(new BigDecimal("300"));
   return p;
 }

 @Test void ranksTwoBuildersAndExplainsCriteria(){
   login("CASE_READ");var a=profile(UUID.fromString("00000000-0000-0000-0000-000000000001"),Map.of("available",true,
      "districts",List.of("District 2"),"maxProjectAreaM2",400,"capitalCapacity",1000,"experienceYears",7));
   var b=profile(UUID.fromString("00000000-0000-0000-0000-000000000002"),Map.of("available",false,"experienceYears",1));
   when(properties.findByCaseId(caseId)).thenReturn(Optional.of(property()));
   when(builders.findByVerifiedTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(b,a)));
   var result=service.match(caseId,new BigDecimal("500"),10);
   assertEquals(1,result.candidateCount()); // unavailable builder is excluded, not boosted
   assertEquals(a.getId(),result.matches().get(0).builderId());
   assertEquals(100,result.matches().get(0).score());
   assertEquals(BuilderMatchingService.CRITERIA_VERSION,result.matches().get(0).evidence().get("criteriaVersion"));
 }
 @Test void unknownAvailabilityGetsNoBonus(){
   login("CASE_READ");var a=profile(UUID.randomUUID(),Map.of());
   when(properties.findByCaseId(caseId)).thenReturn(Optional.of(property()));
   when(builders.findByVerifiedTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(a)));
   assertEquals(20,service.match(caseId,null,10).matches().get(0).score());
 }
 @Test void equalScoreHasStableUuidTieBreak(){
   login("CASE_READ");var a=profile(UUID.fromString("00000000-0000-0000-0000-000000000001"),Map.of());
   var b=profile(UUID.fromString("00000000-0000-0000-0000-000000000002"),Map.of());
   when(properties.findByCaseId(caseId)).thenReturn(Optional.of(property()));
   when(builders.findByVerifiedTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(b,a)));
   assertEquals(a.getId(),service.match(caseId,null,10).matches().get(0).builderId());
 }
 @Test void deniesCaseDiscoveryForCallerWithoutCaseAuthority(){
   login("ROLE_TESTER");assertThrows(ResponseStatusException.class,()->service.match(caseId,null,10));
   verifyNoInteractions(access,properties,builders);
 }
 @Test void invalidLimitFailsBeforePropertyLookup(){
   login("CASE_READ");assertThrows(ResponseStatusException.class,()->service.match(caseId,null,0));
   verifyNoInteractions(properties,builders);
 }
}
