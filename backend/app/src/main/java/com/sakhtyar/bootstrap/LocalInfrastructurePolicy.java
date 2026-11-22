package com.sakhtyar.bootstrap;

import java.util.Set;

/**
 * Only observability sidecars are optional for local application boot.
 * All unknown services remain mandatory (fail closed) until explicitly classified.
 * The database, Redis and MinIO are never downgraded to optional.
 */
final class LocalInfrastructurePolicy {
    private static final Set<String> OPTIONAL_OBSERVABILITY = Set.of(
            "prometheus", "grafana", "tempo", "loki", "alloy"
    );

    private LocalInfrastructurePolicy() {
    }

    static boolean isRequired(String service) {
        return !OPTIONAL_OBSERVABILITY.contains(service);
    }

    static boolean requiredServiceIsReady(String service, String state) {
        if (service.equals("minio")) {
            // Existing local MinIO image has no Docker healthcheck; HTTP is probed later.
            return "running|healthy".equals(state)
                    || "running|no-healthcheck".equals(state);
        }
        // Unknown future services, as well as db and redis, must prove healthy.
        return "running|healthy".equals(state);
    }

    static boolean optionalServiceIsReady(String state) {
        return "running|healthy".equals(state)
                || "running|no-healthcheck".equals(state);
    }
}
