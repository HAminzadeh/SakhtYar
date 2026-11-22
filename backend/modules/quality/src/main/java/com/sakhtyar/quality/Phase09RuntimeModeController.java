package com.sakhtyar.quality;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Mode introspection remains available in production; no test action is exposed here. */
@RestController
@RequestMapping("/api/v1/admin/simulations")
public final class Phase09RuntimeModeController {
    private final Phase09RuntimePolicy policy;
    public Phase09RuntimeModeController(Phase09RuntimePolicy policy) { this.policy=policy; }
    @GetMapping("/runtime-mode") public Map<String,Object> mode(Authentication actor) {
        if(actor==null || !actor.isAuthenticated() || actor.getAuthorities()==null ||
           actor.getAuthorities().stream().noneMatch(x->"USER_MANAGE".equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Simulation administrator required");
        return policy.publicMode();
    }
}
