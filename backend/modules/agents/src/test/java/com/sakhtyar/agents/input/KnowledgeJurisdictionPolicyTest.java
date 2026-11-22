package com.sakhtyar.agents.input;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class KnowledgeJurisdictionPolicyTest {
    @Test
    void localSourceMustBeExplicitlyAllowed() {
        UUID iran = UUID.randomUUID();
        UUID canada = UUID.randomUUID();
        Set<UUID> iranSources = Set.of(iran);

        assertTrue(sourceAllowed(null, iranSources));
        assertTrue(sourceAllowed(iran, iranSources));
        assertFalse(sourceAllowed(canada, iranSources));
    }

    private static boolean sourceAllowed(UUID sourceId, Set<UUID> allowed) {
        return sourceId == null || allowed.contains(sourceId);
    }
}