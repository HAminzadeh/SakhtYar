package com.sakhtyar.integration.neshan;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Service credentials are bound from deployment secrets; never log or expose this object.
 * Network egress is opt-in: APP_INTEGRATIONS_NESHAN_OUTBOUND_ENABLED=true.
 */
@ConfigurationProperties(prefix = "app.integrations.neshan")
public record NeshanProperties(
        boolean enabled,
        String baseUrl,
        String serviceApiKey,
        int timeoutSeconds,
        boolean outboundEnabled,
        int maxRequestsPerMinute,
        int circuitFailureThreshold,
        int circuitOpenSeconds
) {
    @ConstructorBinding
    public NeshanProperties { }

    /** Compatibility constructor for existing offline MockRestServiceServer tests. */
    public NeshanProperties(boolean enabled, String baseUrl, String serviceApiKey, int timeoutSeconds) {
        this(enabled, baseUrl, serviceApiKey, timeoutSeconds, true, 60, 5, 30);
    }

    public int effectiveRequestsPerMinute() {
        return maxRequestsPerMinute < 1 ? 60 : Math.min(maxRequestsPerMinute, 600);
    }

    public int effectiveFailureThreshold() {
        return circuitFailureThreshold < 1 ? 5 : Math.min(circuitFailureThreshold, 20);
    }

    public int effectiveOpenSeconds() {
        return circuitOpenSeconds < 1 ? 30 : Math.min(circuitOpenSeconds, 3600);
    }
}
