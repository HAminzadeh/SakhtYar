package com.sakhtyar.bootstrap;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Runs before DevSeedRunner; conflicts fail closed without modifying environment variables. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class Phase09StartupGuard implements ApplicationRunner {
    private final Environment environment;
    public Phase09StartupGuard(Environment environment) { this.environment=environment; }
    @Override public void run(ApplicationArguments args) {
        boolean official=Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p->"production".equals(p)||"development".equals(p));
        if(!official) return; // legacy local workflow remains compatible
        if(environment.getProperty("app.dev-seed.enabled",Boolean.class,false))
            throw new IllegalStateException("Phase09: public Dev Seed must be disabled in development/production");
        if(environment.getProperty("spring.flyway.enabled",Boolean.class,false))
            throw new IllegalStateException("Phase09: automatic Flyway is forbidden in official profiles; migrate separately");
        // Both active simultaneously is always considered production for E2E endpoint availability.
    }
}
