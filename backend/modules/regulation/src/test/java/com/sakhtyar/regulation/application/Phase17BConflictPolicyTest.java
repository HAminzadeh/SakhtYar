package com.sakhtyar.regulation.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.regulation.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class Phase17BConflictPolicyTest {
    private static UrbanRuleEntity rule(String city,String value){
        UrbanRuleEntity r=mock(UrbanRuleEntity.class);
        when(r.getId()).thenReturn(UUID.randomUUID());
        when(r.getRuleType()).thenReturn(UrbanRuleType.MAX_FAR);
        when(r.getJurisdictionCountry()).thenReturn("IR");
        when(r.getJurisdictionCity()).thenReturn(city);
        when(r.getParameters()).thenReturn(Map.of("maxFar",value));
        return r;
    }
    @Test void equalLimitsAreNotInConflict(){
        assertTrue(UrbanRuleConflictPolicy.conflicts(List.of(rule("Tehran","2.50"),rule("Tehran","2.5"))).isEmpty());
    }
    @Test void divergentCityLimitsRequireReview(){
        var a=rule("Tehran","2.5");var b=rule("Tehran","3");
        assertEquals(Set.of(a.getId(),b.getId()),UrbanRuleConflictPolicy.conflicts(List.of(a,b)));
    }
    @Test void disjointCitiesDoNotConflict(){
        assertTrue(UrbanRuleConflictPolicy.conflicts(List.of(rule("Tehran","2.5"),rule("Shiraz","3"))).isEmpty());
    }
    @Test void nationalAndLocalLimitsRequireReviewWhenDivergent(){
        assertEquals(2,UrbanRuleConflictPolicy.conflicts(List.of(rule(null,"2.5"),rule("Tehran","3"))).size());
    }
}
