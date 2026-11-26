package com.sakhtyar.regulation.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.regulation.domain.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class Phase17BBindingInvalidationTest {
    private UrbanRuleEntity rule() {
        return new UrbanRuleEntity(UUID.randomUUID(),"URB-001","قانون آزمایشی",null,
            UrbanRuleType.MAX_FAR,"IR",null,"Tehran",null,"RESIDENTIAL",
            UUID.randomUUID(),null,null,null,20,true,Map.of("maxFar","2.5"),"tester",Instant.now());
    }
    private void bound(UrbanRuleEntity rule) throws Exception {
        Field field=UrbanRuleEntity.class.getDeclaredField("verifiedKnowledgeRuleId");
        field.setAccessible(true);field.set(rule,UUID.randomUUID());
    }
    @Test void changedLegalLimitDropsEvidenceBindingAndDisablesRule() throws Exception {
        UrbanRuleEntity r=rule();bound(r);
        r.update(r.getNameFa(),r.getNameEn(),r.getRuleType(),r.getJurisdictionCountry(),
            r.getJurisdictionProvince(),r.getJurisdictionCity(),r.getJurisdictionDistrict(),r.getPropertyType(),
            r.getSourceId(),r.getSourceUrl(),r.getValidFrom(),r.getValidTo(),r.getPriority(),true,
            Map.of("maxFar","3"),Instant.now());
        assertNull(r.getVerifiedKnowledgeRuleId());assertFalse(r.isActive());
    }
    @Test void cosmeticNameChangeKeepsApprovedBinding() throws Exception {
        UrbanRuleEntity r=rule();bound(r);UUID previous=r.getVerifiedKnowledgeRuleId();
        r.update("نام اصلاحی",r.getNameEn(),r.getRuleType(),r.getJurisdictionCountry(),
            r.getJurisdictionProvince(),r.getJurisdictionCity(),r.getJurisdictionDistrict(),r.getPropertyType(),
            r.getSourceId(),r.getSourceUrl(),r.getValidFrom(),r.getValidTo(),r.getPriority(),true,
            r.getParameters(),Instant.now());
        assertEquals(previous,r.getVerifiedKnowledgeRuleId());assertTrue(r.isActive());
    }
}
