package com.sakhtyar.quality;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Phase02FixtureCatalogControllerTest {
    @Test void deniesTesterAndAllowsAdminReadOnlyCatalog() {
        Phase02FixtureCatalogController c=new Phase02FixtureCatalogController();
        var tester=new UsernamePasswordAuthenticationToken("tester",null,
                List.of(new SimpleGrantedAuthority("QUALITY_VIEW"),new SimpleGrantedAuthority("QUALITY_RUN_SAFE")));
        assertThrows(ResponseStatusException.class,()->c.contracts(tester));
        var admin=new UsernamePasswordAuthenticationToken("admin",null,
                List.of(new SimpleGrantedAuthority("USER_MANAGE")));
        var result=c.contracts(admin);
        assertEquals(24,result.get("total"));
        assertEquals(false,result.get("writesEnabled"));
    }
}
