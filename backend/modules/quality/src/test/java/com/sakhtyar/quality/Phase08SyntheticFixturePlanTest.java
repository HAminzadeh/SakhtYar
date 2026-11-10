package com.sakhtyar.quality;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase08SyntheticFixturePlanTest {
    @Test void deterministicIdsAreNamespacedToRunAndWorkspaceWithoutSql() {
        UUID workspace=UUID.randomUUID(),run=UUID.randomUUID();
        var a=Phase08SyntheticFixturePlan.prepare(workspace,run);
        assertEquals(a,Phase08SyntheticFixturePlan.prepare(workspace,run));
        assertNotEquals(a.testCaseId(),Phase08SyntheticFixturePlan.prepare(UUID.randomUUID(),run).testCaseId());
        assertNotEquals(a.testCaseId(),Phase08SyntheticFixturePlan.prepare(workspace,UUID.randomUUID()).testCaseId());
        assertEquals(300,a.landAreaSquareMetres());
        assertEquals(100,a.ownerASharePercent()+a.ownerBSharePercent());
        assertFalse(a.databaseWritesAuthorized());
        assertNotEquals(a.ownerAId(),a.ownerBId());
        assertNotEquals(a.testCaseId(),a.testPropertyId());
    }
    @Test void phaseOneProofCatalogCoversExactlySevenTargetsWithoutFakeAcceptance() {
        var targets=Phase08SecurityProofCatalog.all();
        assertEquals(7,targets.size());
        assertEquals(7,targets.stream().map(Phase08SecurityProofCatalog.Requirement::scenarioId).distinct().count());
        assertTrue(targets.stream().allMatch(x->x.report().get("businessAcceptance").equals("BLOCKED_UNTIL_REAL_PROOF")));
        assertTrue(targets.stream().anyMatch(x->x.scenarioId().equals("E2E-05") &&
            x.requiredRealProof().stream().anyMatch(s->s.contains("BLOCKED"))));
    }
}
