package com.sakhtyar.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class Phase09ProfileSafetyInitializerTest {
    @Test void developmentAndProductionRejectImplicitMutationBeforeStartup() {
        for(String profile:new String[]{"development","production"}) {
            var seed=new MockEnvironment();seed.setActiveProfiles(profile);
            seed.setProperty("app.dev-seed.enabled","true");
            assertThrows(IllegalStateException.class,()->Phase09ProfileSafetyInitializer.validate(seed));
            var flyway=new MockEnvironment();flyway.setActiveProfiles(profile);
            flyway.setProperty("spring.flyway.enabled","true");
            assertThrows(IllegalStateException.class,()->Phase09ProfileSafetyInitializer.validate(flyway));
        }
    }
    @Test void officialProfilesWithBothGuardsDisabledAreAccepted() {
        var env=new MockEnvironment();env.setActiveProfiles("development");
        env.setProperty("app.dev-seed.enabled","false");
        env.setProperty("spring.flyway.enabled","false");
        assertDoesNotThrow(()->Phase09ProfileSafetyInitializer.validate(env));
    }
    @Test void legacyLocalRemainsUnchanged() {
        var local=new MockEnvironment();local.setActiveProfiles("local");
        local.setProperty("app.dev-seed.enabled","true");
        assertDoesNotThrow(()->Phase09ProfileSafetyInitializer.validate(local));
    }
}
