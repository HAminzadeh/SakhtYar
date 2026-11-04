package com.sakhtyar.quality;

import java.util.Collection;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only admin inventory; does not provision fixture roles, issue network calls or mutate DB. */
@RestController
public final class Phase02FixtureCatalogController {
    @GetMapping("/api/v1/admin/simulations/e2e/fixture-contracts")
    public Map<String,Object> contracts(Authentication authentication) {
        if(authentication==null || !authentication.isAuthenticated() ||
           authentication.getAuthorities()==null || authentication.getAuthorities().stream()
               .noneMatch(a->"USER_MANAGE".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Simulation administrator required");
        Collection<Phase02FixtureContract> contracts=Phase02FixtureCatalog.all();
        return Map.of("catalogVersion",2,"contracts",contracts,"total",contracts.size(),
            "writesEnabled",false,"fullBusinessWorkflowsVerified",0,
            "warning","Phase02 contracts are not fixture execution. Only existing E2E-14 limited CORE fixture is implemented.");
    }
}
