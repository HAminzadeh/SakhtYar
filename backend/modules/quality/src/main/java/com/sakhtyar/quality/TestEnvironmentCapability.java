package com.sakhtyar.quality;

/** Phase02 handoff: no provider may claim TEST isolation without concrete, inspectable evidence. */
public interface TestEnvironmentCapability {
    IsolationEvidence checkRestrictedEnvironment();

    default void requireIsolated() {
        IsolationEvidence evidence = checkRestrictedEnvironment();
        if (evidence == null || !evidence.restrictedPrincipal() || !evidence.syntheticOnly() ||
            !evidence.externalEffectsDenied() || !evidence.noRealAccess()) {
            throw new SecurityException("Isolated TEST environment has not been proven");
        }
    }
}
