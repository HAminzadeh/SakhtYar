package com.sakhtyar.quality.t10;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * Deny-by-default policy for TEST outbound requests. NOT yet wired into the
 * production adapters; all callers MUST invoke this at the outbound boundary.
 */
public final class SimulationSafetyPolicy {
    private SimulationSafetyPolicy() {}

    public static void assertOutboundAllowed(boolean testScope, URI target,
                                              Set<String> sandboxHosts) {
        Objects.requireNonNull(target, "target");
        if (!testScope) return; // Operational authorization remains adapter-specific.
        if (!"https".equalsIgnoreCase(target.getScheme())
                || target.getHost() == null
                || target.getUserInfo() != null
                || !Objects.requireNonNull(sandboxHosts, "sandboxHosts")
                       .contains(target.getHost().toLowerCase(java.util.Locale.ROOT))) {
            throw new SecurityException("T10 TEST outbound request blocked");
        }
    }
}
