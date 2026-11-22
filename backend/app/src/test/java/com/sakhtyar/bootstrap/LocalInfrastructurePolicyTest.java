package com.sakhtyar.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalInfrastructurePolicyTest {
    @Test
    void coreServicesAreAlwaysMandatoryAndMustBeHealthy() {
        for (String service : new String[] {"db", "redis", "minio"}) {
            assertTrue(LocalInfrastructurePolicy.isRequired(service));
        }
        assertTrue(LocalInfrastructurePolicy.requiredServiceIsReady("db", "running|healthy"));
        assertTrue(LocalInfrastructurePolicy.requiredServiceIsReady("redis", "running|healthy"));
        assertTrue(LocalInfrastructurePolicy.requiredServiceIsReady("minio", "running|no-healthcheck"));
        assertFalse(LocalInfrastructurePolicy.requiredServiceIsReady("db", "running|no-healthcheck"));
        assertFalse(LocalInfrastructurePolicy.requiredServiceIsReady("redis", "running|starting"));
        assertFalse(LocalInfrastructurePolicy.requiredServiceIsReady("minio", "restarting|no-healthcheck"));
    }

    @Test
    void knownObservabilitySidecarsAreOptional() {
        for (String service : new String[] {"tempo", "prometheus", "grafana", "loki", "alloy"}) {
            assertFalse(LocalInfrastructurePolicy.isRequired(service));
        }
        assertFalse(LocalInfrastructurePolicy.optionalServiceIsReady("restarting|no-healthcheck"));
        assertTrue(LocalInfrastructurePolicy.optionalServiceIsReady("running|no-healthcheck"));
    }

    @Test
    void unknownFutureServicesAreNeverSilentlyOptional() {
        assertTrue(LocalInfrastructurePolicy.isRequired("payment-gateway"));
        assertTrue(LocalInfrastructurePolicy.isRequired("new-service"));
        assertFalse(LocalInfrastructurePolicy.requiredServiceIsReady("new-service", "exited|no-healthcheck"));
        assertFalse(LocalInfrastructurePolicy.requiredServiceIsReady("new-service", "running|no-healthcheck"));
    }
}
