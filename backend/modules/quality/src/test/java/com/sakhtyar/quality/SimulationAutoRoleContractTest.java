package com.sakhtyar.quality;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure tests of workspace-to-role/policy identity. No DB required. */
class SimulationAutoRoleContractTest {
    @Test void eachWorkspaceHasItsOwnNonSharedDatabaseIdentity() {
        UUID a=UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID b=UUID.fromString("22222222-2222-4222-8222-222222222222");
        String role=SimulationCoreFixtureService.role(a);
        assertTrue(role.matches("sakhtyar_tws_[a-f0-9]{32}"));
        assertFalse(role.equals(SimulationCoreFixtureService.role(b)));
        assertTrue(SimulationCoreFixtureService.policy(a).matches("sakhtyar_tws_policy_[a-f0-9]{32}"));
        assertFalse(SimulationCoreFixtureService.policy(a).equals(SimulationCoreFixtureService.policy(b)));
        assertTrue(SimulationCoreFixtureService.policy(a).length()<=63);
    }
    @Test void quoteOperationsCannotInjectSqlViaIdentifiersOrLiterals() {
        assertEquals("\"safe\"\"identifier\"",SimulationCoreFixtureService.quoted("safe\"identifier"));
        assertEquals("'O''Reilly'",SimulationCoreFixtureService.literal("O'Reilly"));
    }
}
