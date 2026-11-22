package com.sakhtyar.contract.application;

import static com.sakhtyar.contract.api.ContractDtos.UpsertContractRequest;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ContractInputPolicyTest {
    private UpsertContractRequest request(String status,Instant signed,Map<String,Object> terms,Map<String,Object> metadata) {
        return new UpsertContractRequest("CT-TEST","PARTICIPATION_IN_CONSTRUCTION",status,
                "Synthetic contract",signed,LocalDate.of(2027,1,1),LocalDate.of(2027,12,31),"1.0",terms,metadata);
    }
    @Test void unsignedDraftAccepted() {
        assertDoesNotThrow(()->ContractInputPolicy.validateDraft(request("DRAFT",null,
                Map.of("PARTIES","synthetic parties"),Map.of())));
    }
    @Test void signedAndApprovedStatesRejected() {
        for(String state:List.of("SIGNED","APPROVED","ARCHIVED","PENDING"))
            assertThrows(ResponseStatusException.class,
                    ()->ContractInputPolicy.validateDraft(request(state,null,Map.of(),Map.of())));
    }
    @Test void forgedSigningTimestampRejected() {
        assertThrows(ResponseStatusException.class,
                ()->ContractInputPolicy.validateDraft(request("DRAFT",Instant.now(),Map.of(),Map.of())));
    }
    @Test void fakeLegalEvidenceMetadataRejected() {
        assertThrows(ResponseStatusException.class,
                ()->ContractInputPolicy.validateDraft(request("DRAFT",null,Map.of(),Map.of("approvedBy","alice"))));
    }
    @Test void invalidDatesRejected() {
        var r=new UpsertContractRequest("CT-TEST","TYPE","DRAFT","Synthetic",null,
                LocalDate.of(2027,12,31),LocalDate.of(2027,1,1),"1.0",Map.of(),Map.of());
        assertThrows(ResponseStatusException.class,()->ContractInputPolicy.validateDraft(r));
    }
    @Test void invalidControlCharacterInCodeRejected() {
        var r=new UpsertContractRequest("CT\nTEST","TYPE","DRAFT","Synthetic",null,
                null,null,"1.0",Map.of(),Map.of());
        assertThrows(ResponseStatusException.class,()->ContractInputPolicy.validateDraft(r));
    }
    @Test void excessivelyNestedTermsRejected() {
        Map<String,Object> inner=Map.of("text","Synthetic");
        for(int i=0;i<10;i++)inner=Map.of("nested",inner);
        Map<String,Object> finalInner=inner;
        assertThrows(ResponseStatusException.class,
                ()->ContractInputPolicy.validateDraft(request("DRAFT",null,finalInner,Map.of())));
    }
    @Test void missingDraftStatusRejectedWithoutNullPointer() {
        assertThrows(ResponseStatusException.class,
                ()->ContractInputPolicy.validateDraft(request(null,null,Map.of(),Map.of())));
    }
    @Test void nullJsonValuesAreRejectedBeforeJpaMapCopy() {
        Map<String,Object> terms=new HashMap<>(); terms.put("PARTIES",null);
        assertThrows(ResponseStatusException.class,
                ()->ContractInputPolicy.validateDraft(request("DRAFT",null,terms,Map.of())));
    }
    @Test void nanIsNeverSilentlyAccepted() {
        assertThrows(ResponseStatusException.class,
                ()->ContractInputPolicy.validateDraft(request("DRAFT",null,Map.of("SHARES",Double.NaN),Map.of())));
    }
}
