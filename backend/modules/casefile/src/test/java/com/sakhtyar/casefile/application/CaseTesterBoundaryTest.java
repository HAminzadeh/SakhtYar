package com.sakhtyar.casefile.application;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CaseTesterBoundaryTest {
    @Test void testerCannotUseRealCaseApis() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("tester", null,
                List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
        try {
            CaseService service = new CaseService(null,null,null);
            assertThrows(ResponseStatusException.class, service::list);
            assertThrows(ResponseStatusException.class, () -> service.get(UUID.randomUUID()));
            assertThrows(ResponseStatusException.class, () -> service.create(null,null));
            assertThrows(ResponseStatusException.class, () -> service.update(UUID.randomUUID(),null));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
