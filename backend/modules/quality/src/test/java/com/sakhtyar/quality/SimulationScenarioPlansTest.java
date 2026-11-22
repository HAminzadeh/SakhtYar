package com.sakhtyar.quality;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationScenarioPlansTest {
    @Test void all24HaveARealPrerequisiteContract() {
        var plans=SimulationScenarioPlans.all();
        assertEquals(24,plans.size());
        assertEquals(24,plans.stream().map(SimulationScenarioPlans.Plan::id).distinct().count());
        for(var p:plans) {
            assertFalse(p.description().isBlank());
            assertFalse(p.requiredTables().isEmpty());
            assertFalse(p.requiredRoutes().isEmpty());
            assertEquals(4,p.preflightSteps().size());
            assertFalse(p.businessSteps().isBlank());
            assertEquals(p.id(),SimulationScenarioPlans.orderedWithPrerequisites(List.of(p.id()))
              .getLast());
        }
    }
    @Test void dependencyGraphIsCompleteAndAcyclic() {
        for(var p:SimulationScenarioPlans.all()) {
            var order=SimulationScenarioPlans.orderedWithPrerequisites(List.of(p.id()));
            assertEquals(order.size(),new HashSet<>(order).size());
            for(var prior:p.dependencies()) assertTrue(order.indexOf(prior)<order.indexOf(p.id()));
        }
        var ordered=SimulationScenarioPlans.orderedWithPrerequisites(
            SimulationScenarioPlans.all().stream().map(SimulationScenarioPlans.Plan::id).toList());
        assertEquals(24,ordered.size());
    }
    @Test void unsafeBusinessWritesCannotBeDeclaredComplete() {
        long limited=SimulationScenarioPlans.all().stream().filter(p->"LIMITED_CORE_FIXTURE".equals(p.businessStatus())).count();
        assertEquals(1,limited);
        assertEquals("E2E-14",SimulationScenarioPlans.all().stream()
            .filter(p->"LIMITED_CORE_FIXTURE".equals(p.businessStatus())).findFirst().orElseThrow().id());
        assertEquals(23,SimulationScenarioPlans.all().stream()
            .filter(p->"NOT_IMPLEMENTED_FULL_E2E".equals(p.businessStatus())).count());
    }
}
