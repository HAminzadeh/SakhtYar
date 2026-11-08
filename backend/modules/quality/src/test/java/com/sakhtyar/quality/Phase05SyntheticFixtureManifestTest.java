package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase05SyntheticFixtureManifestTest {
    private static final UUID W=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID R=UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Test void deterministicSyntheticIdsScopedByRunAndWorkspace(){
        var a=Phase05SyntheticFixtureManifest.plan(W,R,"E2E-15");
        assertEquals(a.resourceIds(),Phase05SyntheticFixtureManifest.plan(W,R,"E2E-15").resourceIds());
        assertNotEquals(a.resourceIds(),Phase05SyntheticFixtureManifest.plan(UUID.randomUUID(),R,"E2E-15").resourceIds());
        assertNotEquals(a.resourceIds(),Phase05SyntheticFixtureManifest.plan(W,UUID.randomUUID(),"E2E-15").resourceIds());
        assertEquals("BLOCKED",a.executionState());
        assertEquals(13,a.resourceIds().size());
    }
    @Test void onlyE2E15Through20AndNoLiveWrites(){
        assertThrows(IllegalArgumentException.class,()->Phase05SyntheticFixtureManifest.plan(W,R,"E2E-14"));
        assertThrows(IllegalArgumentException.class,()->Phase05SyntheticFixtureManifest.plan(W,R,"E2E-21"));
        assertThrows(SecurityException.class,()->Phase05SyntheticFixtureManifest.requireNoWrites(
                Phase05SyntheticFixtureManifest.plan(W,R,"E2E-20")));
    }
}