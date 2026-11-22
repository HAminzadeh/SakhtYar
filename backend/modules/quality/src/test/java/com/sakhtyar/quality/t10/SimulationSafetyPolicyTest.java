package com.sakhtyar.quality.t10;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimulationSafetyPolicyTest {
    @Test void deniesRealHostInTest() {
        assertThrows(SecurityException.class, () ->
            SimulationSafetyPolicy.assertOutboundAllowed(true,
                URI.create("https://example.org/path"), Set.of("sandbox.example.org")));
    }
    @Test void deniesHttpInTest() {
        assertThrows(SecurityException.class, () ->
            SimulationSafetyPolicy.assertOutboundAllowed(true,
                URI.create("http://sandbox.example.org/"), Set.of("sandbox.example.org")));
    }
    @Test void allowsExplicitSandboxHost() {
        assertDoesNotThrow(() ->
            SimulationSafetyPolicy.assertOutboundAllowed(true,
                URI.create("https://sandbox.example.org/"), Set.of("sandbox.example.org")));
    }
}
