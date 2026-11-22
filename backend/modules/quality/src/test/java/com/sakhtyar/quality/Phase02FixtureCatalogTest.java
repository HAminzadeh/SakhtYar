package com.sakhtyar.quality;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase02FixtureCatalogTest {
    @Test void everyScenarioHasFailClosedContract() {
        assertEquals(24,Phase02FixtureCatalog.all().size());
        assertEquals(24,new Phase02FixtureFactories().size());
        for(Phase02FixtureContract c:Phase02FixtureCatalog.all()) {
            assertFalse(c.writesEnabled());
            assertFalse(c.requiredTables().isEmpty());
            assertEquals("BLOCKED",new Phase02FixtureFactories().get(c.scenarioId())
                .prepare(UUID.randomUUID(),UUID.randomUUID()).state());
        }
        assertEquals(Phase02FixtureContract.IsolationMode.LIMITED_CORE_RESTRICTED_LOGIN,
            Phase02FixtureCatalog.get("E2E-14").isolationMode());
        assertEquals(Phase02FixtureContract.IsolationMode.SHARED_DB_TEST_GATED,
            Phase02FixtureCatalog.get("E2E-01").isolationMode());
    }
    @Test void deterministicKeysAreIdempotentAndRunSpecific() {
        UUID w=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        assertEquals(Phase02FixtureKeys.fixtureId(w,a,"E2E-14","root"),
                     Phase02FixtureKeys.fixtureId(w,a,"E2E-14","root"));
        assertNotEquals(Phase02FixtureKeys.fixtureId(w,a,"E2E-14","root"),
                        Phase02FixtureKeys.fixtureId(w,b,"E2E-14","root"));
        assertThrows(IllegalArgumentException.class,()->Phase02FixtureKeys.fixtureId(w,a,"E2E-14","../real"));
        Phase02FixtureFactory f=new Phase02FixtureFactories().get("E2E-14");
        assertEquals(f.prepare(a,w).deterministicFixtureId(),f.teardown(a,w).deterministicFixtureId());
        assertEquals("false",f.prepare(a,w).auditLabels().get("writePerformed"));
    }
    @Test void sandboxKeysRejectTraversalAndOtherRuns() {
        UUID w=UUID.randomUUID(),r=UUID.randomUUID();
        Phase02SandboxNamespaces a=new Phase02SandboxNamespaces(w,r);
        Phase02SandboxNamespaces b=new Phase02SandboxNamespaces(w,UUID.randomUUID());
        assertTrue(a.ownsMinioKey(a.minioKey("safe/report.json")));
        assertFalse(b.ownsMinioKey(a.minioKey("safe/report.json")));
        assertTrue(a.ownsRedisKey(a.redisKey("counts")));
        assertFalse(b.ownsRedisKey(a.redisKey("counts")));
        assertThrows(IllegalArgumentException.class,()->a.minioKey("../real"));
        assertFalse(a.ownsMinioKey("test/"+w+"/"+r+"/../real"));
    }
}
