package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase02BlockedFixtureLifecycleTest {
    @Test void all24FactoriesRemainsBlockedForEveryLifecycleOperation() {
        var engine=new Phase02BlockedFixtureLifecycle();
        for(var p:Phase02FixtureCatalog.all()) {
            var workspace=UUID.randomUUID();var run=UUID.randomUUID();
            for(var a:Phase02BlockedFixtureLifecycle.Action.values()) {
                var event=engine.perform(a,run,workspace,p.scenarioId());
                assertEquals("BLOCKED",event.state());
                assertEquals(workspace,event.fixture().workspaceId());
                assertEquals(run,event.fixture().runId());
                assertEquals(event,engine.last(event.fixture()));
            }
        }
    }
    @Test void fixtureIdStableAndDifferentRunsNotConfused() {
        var engine=new Phase02BlockedFixtureLifecycle();
        UUID w=UUID.randomUUID(), r=UUID.randomUUID();
        var one=engine.perform(Phase02BlockedFixtureLifecycle.Action.PREPARE,r,w,"E2E-14");
        var repeat=engine.perform(Phase02BlockedFixtureLifecycle.Action.PREPARE,r,w,"E2E-14");
        var other=engine.perform(Phase02BlockedFixtureLifecycle.Action.PREPARE,UUID.randomUUID(),w,"E2E-14");
        assertEquals(one.fixture().fixtureId(),repeat.fixture().fixtureId());
        assertNotEquals(one.fixture().fixtureId(),other.fixture().fixtureId());
        assertThrows(IllegalArgumentException.class,()->engine.perform(
            Phase02BlockedFixtureLifecycle.Action.TEARDOWN,r,w,"E2E-25"));
    }
}
