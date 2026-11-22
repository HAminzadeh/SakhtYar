package com.sakhtyar.quality;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class Phase09RuntimePolicyTest {
    @Test void developmentCanInspectButProfileNeverAuthorizesBusinessWrites() {
        var env=new MockEnvironment();env.setActiveProfiles("development");
        var policy=new Phase09RuntimePolicy(env);
        assertEquals("development",policy.profile());
        assertTrue(policy.development());
        policy.requireDevelopment();
        assertEquals(false,policy.publicMode().get("businessWritesAuthorizedByProfile"));
    }
    @Test void productionAlwaysOverridesDevelopment() {
        var env=new MockEnvironment();env.setActiveProfiles("development","production");
        var policy=new Phase09RuntimePolicy(env);
        assertEquals("production",policy.profile());
        assertFalse(policy.development());
        var ex=assertThrows(ResponseStatusException.class,policy::requireDevelopment);
        assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
    }
    @Test void legacyLocalDoesNotGainBusinessRunPrivilege() {
        var env=new MockEnvironment();env.setActiveProfiles("local");
        var policy=new Phase09RuntimePolicy(env);
        assertEquals("legacy-local",policy.profile());
        assertThrows(ResponseStatusException.class,policy::requireDevelopment);
    }
}
