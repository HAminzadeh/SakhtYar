package com.sakhtyar.regulation.application;

import com.sakhtyar.regulation.domain.UrbanRuleEntity;
import com.sakhtyar.regulation.domain.UrbanRuleType;
import java.math.BigDecimal;
import java.util.*;

/** Conservative conflict detection: legal priority cannot be inferred from numeric priority. */
public final class UrbanRuleConflictPolicy {
    private UrbanRuleConflictPolicy() {}

    private static String parameter(UrbanRuleType type) {
        return switch (type) {
            case MAX_FLOORS -> "maxFloors";
            case MAX_FAR -> "maxFar";
            case MAX_COVERAGE_PERCENT -> "maxCoveragePercent";
            case MIN_PASSAGE_WIDTH -> "minPassageWidthM";
            case MIN_FRONTAGE -> "minFrontageM";
            case MAX_HEIGHT -> "maxHeightM";
        };
    }
    public static boolean sameOrOverlappingScope(String a, String b) {
        return a == null || a.isBlank() || b == null || b.isBlank() || a.strip().equalsIgnoreCase(b.strip());
    }
    public static Set<UUID> conflicts(List<UrbanRuleEntity> rules) {
        Set<UUID> conflicts=new LinkedHashSet<>();
        for (int i=0;i<rules.size();i++) for (int j=i+1;j<rules.size();j++) {
            UrbanRuleEntity a=rules.get(i), b=rules.get(j);
            if(a.getRuleType()!=b.getRuleType()) continue;
            if(!sameOrOverlappingScope(a.getJurisdictionCountry(),b.getJurisdictionCountry()) ||
               !sameOrOverlappingScope(a.getJurisdictionProvince(),b.getJurisdictionProvince()) ||
               !sameOrOverlappingScope(a.getJurisdictionCity(),b.getJurisdictionCity()) ||
               !sameOrOverlappingScope(a.getJurisdictionDistrict(),b.getJurisdictionDistrict()) ||
               !sameOrOverlappingScope(a.getPropertyType(),b.getPropertyType())) continue;
            String key=parameter(a.getRuleType());
            Object av=a.getParameters().get(key),bv=b.getParameters().get(key);
            boolean disagreement;
            try {
                disagreement=av==null || bv==null ||
                    new BigDecimal(av.toString()).compareTo(new BigDecimal(bv.toString()))!=0;
            } catch(RuntimeException ex) { disagreement=true; }
            if(disagreement) {conflicts.add(a.getId());conflicts.add(b.getId());}
        }
        return Collections.unmodifiableSet(conflicts);
    }
}
