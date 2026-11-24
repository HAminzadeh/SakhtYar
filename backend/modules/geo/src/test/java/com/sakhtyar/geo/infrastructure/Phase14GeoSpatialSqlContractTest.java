package com.sakhtyar.geo.infrastructure;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase14GeoSpatialSqlContractTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void queryFiltersAccessAndScopeBeforeMaterialization() {
        String query = GeoSpatialRepository.NEARBY_SQL;
        assertTrue(query.contains("c.data_scope = 'REAL'"));
        assertTrue(query.contains("p.data_scope = 'REAL'"));
        assertTrue(query.contains("m.active = true"));
        assertTrue(query.contains("m.data_scope = 'REAL'"));
        assertTrue(query.contains("c.created_by"));
        assertTrue(query.contains("ST_DWithin"));
        assertTrue(query.contains("ST_MakePoint(:lng, :lat)"));
        assertTrue(query.contains("4326"));
        assertTrue(query.contains("ORDER BY distance_meters ASC, p.case_id ASC"));
        assertTrue(query.contains("LIMIT :limit"));
        assertFalse(query.contains("SELECT *"));
    }

    @Test void repositoryRejectsTesterBeforeBuildingSql() {
        var client = mock(JdbcClient.class);
        var repo = new GeoSpatialRepository(client);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "unused", AuthorityUtils.createAuthorityList("CASE_READ", "ROLE_TESTER")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> repo.findNearbyProperties(BigDecimal.ZERO, BigDecimal.ZERO, 100)).getStatusCode());
        verifyNoInteractions(client);
    }

    @Test void repositoryRejectsInvalidCoordinatesBeforeSql() {
        var client = mock(JdbcClient.class);
        var repo = new GeoSpatialRepository(client);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "owner", "unused", AuthorityUtils.createAuthorityList("CASE_READ")));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> repo.findNearbyProperties(new BigDecimal("99"), BigDecimal.ZERO, 100)).getStatusCode());
        verifyNoInteractions(client);
    }
}
