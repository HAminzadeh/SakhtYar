package com.sakhtyar.casefile.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class Phase08CaseIdorAndMemberTest {
    private final CaseRepository repository = mock(CaseRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CaseAccessService access = new CaseAccessService(repository, jdbc);

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private void principal(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static CaseEntity caseOf(UUID id, String creator, String scope) {
        CaseEntity entity = mock(CaseEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getCreatedBy()).thenReturn(creator);
        when(entity.getDataScope()).thenReturn(scope);
        return entity;
    }

    @Test void twoCreatorsAndOutsiderCannotReadEachOthersCases() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        CaseEntity first = caseOf(a, "ownerA", "REAL"), second = caseOf(b, "ownerB", "REAL");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(false);
        principal("ownerA", "CASE_READ", "CASE_WRITE");
        assertTrue(access.canRead(first));
        assertFalse(access.canRead(second));
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> access.requireWrite(second)).getStatusCode());
        principal("ownerB", "CASE_READ", "CASE_WRITE");
        assertTrue(access.canRead(second));
        assertFalse(access.canRead(first));
        principal("outsider", "CASE_READ", "CASE_WRITE");
        assertFalse(access.canRead(first));
        assertFalse(access.canRead(second));
    }

    @Test void readOnlyMemberCannotWriteEvenWithActiveMembership() {
        UUID id = UUID.randomUUID(); CaseEntity entity = caseOf(id, "owner", "REAL");
        principal("member", "CASE_READ");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(id), eq("member"))).thenReturn(true);
        assertTrue(access.canRead(entity));
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> access.requireWrite(entity)).getStatusCode());
        principal("member", "CASE_READ", "CASE_WRITE");
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> access.requireWrite(entity)).getStatusCode());
    }

    @Test void creatorWithoutWriteAuthorityCannotBypassDomainBoundary() {
        CaseEntity entity = caseOf(UUID.randomUUID(), "owner", "REAL");
        principal("owner", "CASE_READ");
        assertTrue(access.canRead(entity));
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> access.requireWrite(entity)).getStatusCode());
        principal("owner", "CASE_READ", "CASE_WRITE");
        assertDoesNotThrow(() -> access.requireWrite(entity));
    }

    @Test void testWorkspaceRecordsAreNeverVisibleThroughRealApi() {
        CaseEntity fixture = caseOf(UUID.randomUUID(), "owner", "TEST");
        principal("owner", "ROLE_ADMIN", "CASE_READ", "CASE_WRITE");
        assertFalse(access.canRead(fixture));
        assertThrows(ResponseStatusException.class, () -> access.requireWrite(fixture));
        principal("tester", "ROLE_TESTER", "ROLE_ADMIN", "CASE_READ", "CASE_WRITE");
        assertFalse(access.canRead(caseOf(UUID.randomUUID(), "tester", "REAL")));
    }
}
