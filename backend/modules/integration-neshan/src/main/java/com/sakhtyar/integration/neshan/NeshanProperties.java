package com.sakhtyar.integration.neshan;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations.neshan")
public record NeshanProperties(
        boolean enabled,
        String baseUrl,
        String serviceApiKey,
        int timeoutSeconds
) {
}
