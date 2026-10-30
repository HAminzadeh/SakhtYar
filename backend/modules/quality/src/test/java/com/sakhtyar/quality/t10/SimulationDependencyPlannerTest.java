package com.sakhtyar.quality.t10;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SimulationDependencyPlannerTest {
    @Test void ordersPrerequisitesFirst() {
        assertEquals(List.of("case","builder","finance"),
            SimulationDependencyPlanner.order(Map.of(
                "finance",List.of("builder"), "builder",List.of("case"), "case",List.of())));
    }
    @Test void deniesMissingDependency() {
        assertThrows(IllegalArgumentException.class, () ->
            SimulationDependencyPlanner.order(Map.of("finance",List.of("missing"))));
    }
    @Test void deniesCycles() {
        assertThrows(IllegalArgumentException.class, () ->
            SimulationDependencyPlanner.order(Map.of("a",List.of("b"), "b",List.of("a"))));
    }
}
