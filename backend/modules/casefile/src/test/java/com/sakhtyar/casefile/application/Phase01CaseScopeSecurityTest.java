package com.sakhtyar.casefile.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class Phase01CaseScopeSecurityTest {
    private final CaseRepository repository = mock(CaseRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CaseAccessService access = new CaseAccessService(repository, jdbc);

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private static void authenticate(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        java.util.Arrays.stream(authorities)
                                .map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void evenAdministratorCannotReadOrWriteTestRowThroughRealCaseApi() {
        CaseEntity testRow = mock(CaseEntity.class);
        when(testRow.getDataScope()).thenReturn("TEST");
        authenticate("admin", "ROLE_ADMIN", "CASE_READ", "CASE_WRITE");
        assertFalse(access.canRead(testRow));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> access.requireWrite(testRow));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void testerWithExtraAuthoritiesCannotAccessRealCase() {
        CaseEntity realRow = mock(CaseEntity.class);
        when(realRow.getDataScope()).thenReturn("REAL");
        authenticate("tester", "ROLE_TESTER", "ROLE_ADMIN", "CASE_READ", "CASE_WRITE");
        assertFalse(access.canRead(realRow));
        assertThrows(ResponseStatusException.class, () -> access.requireWrite(realRow));
        verifyNoInteractions(jdbc);
    }

    @Test
    void anonymousUserIsDenied() {
        CaseEntity realRow = mock(CaseEntity.class);
        when(realRow.getDataScope()).thenReturn("REAL");
        assertFalse(access.canRead(realRow));
        verifyNoInteractions(jdbc);
    }
}
