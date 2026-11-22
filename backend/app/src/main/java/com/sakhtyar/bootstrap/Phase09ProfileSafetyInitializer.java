package com.sakhtyar.bootstrap;

import java.util.Arrays;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Fail early, before Flyway, repositories, startup runners or local infrastructure. */
public final class Phase09ProfileSafetyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static void validate(Environment e) {
        boolean official=Arrays.stream(e.getActiveProfiles())
            .anyMatch(s->"development".equals(s)||"production".equals(s));
        if(!official) return;
        if(e.getProperty("app.dev-seed.enabled",Boolean.class,false))
            throw new IllegalStateException("Phase09 startup denied: app.dev-seed.enabled must be false");
        if(e.getProperty("spring.flyway.enabled",Boolean.class,false))
            throw new IllegalStateException("Phase09 startup denied: automatic Flyway must be false");
    }
    @Override public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }
}
