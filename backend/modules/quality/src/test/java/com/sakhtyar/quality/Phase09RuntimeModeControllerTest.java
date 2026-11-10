package com.sakhtyar.quality;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class Phase09RuntimeModeControllerTest {
    @Test void authenticatedAdminGetsSafeProductionMode() {
        var env=new MockEnvironment();env.setActiveProfiles("production");
        var ctrl=new Phase09RuntimeModeController(new Phase09RuntimePolicy(env));
        var admin=new UsernamePasswordAuthenticationToken("owner",null,
            List.of(new SimpleGrantedAuthority("USER_MANAGE")));
        var data=ctrl.mode(admin);
        assertEquals("production",data.get("profile"));
        assertEquals(false,data.get("uiPreflightAvailable"));
    }
    @Test void anonymousAndNonAdminCannotDiscoverMode() {
        var ctrl=new Phase09RuntimeModeController(new Phase09RuntimePolicy(new MockEnvironment()));
        var bad=new UsernamePasswordAuthenticationToken("someone",null,List.of());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
            ()->ctrl.mode(bad)).getStatusCode());
    }
}
