package com.sakhtyar.quality;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase09SafetyBlockerCatalogTest {
    @Test void listsAllElevenRequirementsWithEvidenceNames() {
        assertEquals(11,Phase09SafetyBlockerCatalog.all().size());
        var descriptions=Phase09SafetyBlockerCatalog.report(List.of("DEVELOPMENT_SEED_MUST_BE_DISABLED"));
        assertEquals(11,descriptions.size());
        assertEquals(1,descriptions.stream().filter(x->"BLOCKED".equals(x.get("state"))).count());
        assertTrue(descriptions.stream().allMatch(x->!String.valueOf(x.get("remediationFa")).isBlank()));
    }
    @Test void unknownBlockersAreNotSilentlyDropped() {
        assertTrue(Phase09SafetyBlockerCatalog.report(List.of("UNKNOWN_SECURITY_REQUIREMENT")).stream()
            .anyMatch(x->"UNKNOWN_SECURITY_REQUIREMENT".equals(x.get("code")) && "BLOCKED".equals(x.get("state"))));
    }
}
