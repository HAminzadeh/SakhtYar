package com.sakhtyar.runtime;

import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RuntimeRoleHealthContributor implements HealthIndicator {

    private final RuntimeRole runtimeRole;

    public RuntimeRoleHealthContributor(RuntimeRole runtimeRole) {
        this.runtimeRole = runtimeRole;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetails(Map.of("runtimeRole", runtimeRole.name()))
                .build();
    }
}
