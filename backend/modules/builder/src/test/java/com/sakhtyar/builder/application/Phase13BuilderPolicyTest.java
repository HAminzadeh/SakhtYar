package com.sakhtyar.builder.application;
import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.builder.api.BuilderDtos.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class Phase13BuilderPolicyTest {
 @Test void candidateLinkMayNotForgeSelection(){
   assertThrows(ResponseStatusException.class,()->BuilderPolicy.link(new LinkBuilderRequest(UUID.randomUUID(),"SELECTED","ACCEPTED",true,null,Map.of())));
 }
 @Test void candidateLinkIsAllowed(){
   assertDoesNotThrow(()->BuilderPolicy.link(new LinkBuilderRequest(UUID.randomUUID(),"CANDIDATE","ACTIVE",false,null,Map.of())));
 }
 @Test void shareOutOfRangeAndOverPreciseAreDenied(){
   for(String value:List.of("-1","101","3.12345"))assertThrows(ResponseStatusException.class,()->BuilderPolicy.share(new BigDecimal(value)));
 }
 @Test void proposalRequiresDurationAndNonnegativeShare(){
   assertThrows(ResponseStatusException.class,()->BuilderPolicy.proposal(new SubmitProposalRequest(BigDecimal.TEN,0,Map.of())));
   assertThrows(ResponseStatusException.class,()->BuilderPolicy.proposal(new SubmitProposalRequest(new BigDecimal("120"),12,Map.of())));
 }
 @Test void metadataRejectsNullAndLargeValues(){
   Map<String,Object> invalid=new LinkedHashMap<>();invalid.put("available",null);
   assertThrows(ResponseStatusException.class,()->BuilderPolicy.metadata(invalid));
   assertThrows(ResponseStatusException.class,()->BuilderPolicy.metadata(Map.of("x","a".repeat(9000))));
 }
 @Test void metadataAllowsOrdinaryScoringFields(){
   assertDoesNotThrow(()->BuilderPolicy.metadata(Map.of("available",true,"experienceYears",5,"districts",List.of("1","2"))));
 }
 @Test void unverifiedProfileInputIsStillValidForAdminReview(){
   var r=new UpsertBuilderRequest(" Builder ",null,"123456",null,null,null,null,null,null,"Tehran",null,false,Map.of());
   assertDoesNotThrow(()->BuilderPolicy.profile(r));
 }
}
