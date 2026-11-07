package com.sakhtyar.quality;

import java.net.URI;

/** Phase02 adapter boundary: an unconfigured provider denies all effects by default. */
@FunctionalInterface
public interface ExternalEffectGuard {
    void requireAllowed(FixtureContext fixture, Phase02OutboundGuard.Effect effect, URI endpoint);

    static ExternalEffectGuard denyAll() {
        return (fixture, effect, endpoint) -> {
            throw new SecurityException("TEST outbound side effect is denied");
        };
    }
}
