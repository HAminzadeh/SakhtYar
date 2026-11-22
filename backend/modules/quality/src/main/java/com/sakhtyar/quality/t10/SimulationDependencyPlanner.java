package com.sakhtyar.quality.t10;

import java.util.*;

/** Stable topological ordering; fails closed on missing prerequisites/cycles. */
public final class SimulationDependencyPlanner {
    private SimulationDependencyPlanner() {}
    public static List<String> order(Map<String, ? extends Collection<String>> dependencies) {
        Objects.requireNonNull(dependencies, "dependencies");
        Map<String, Integer> state = new HashMap<>();
        List<String> result = new ArrayList<>();
        for (String name : new TreeSet<>(dependencies.keySet()))
            visit(name, dependencies, state, result);
        return List.copyOf(result);
    }
    private static void visit(String name,
            Map<String, ? extends Collection<String>> deps,
            Map<String, Integer> state, List<String> result) {
        if (!deps.containsKey(name)) throw new IllegalArgumentException("Missing prerequisite: " + name);
        int s = state.getOrDefault(name, 0);
        if (s == 1) throw new IllegalArgumentException("Dependency cycle: " + name);
        if (s == 2) return;
        state.put(name, 1);
        Collection<String> parents = Objects.requireNonNull(deps.get(name), "null prerequisites");
        for (String parent : new TreeSet<>(parents)) visit(parent, deps, state, result);
        state.put(name, 2);
        result.add(name);
    }
}
