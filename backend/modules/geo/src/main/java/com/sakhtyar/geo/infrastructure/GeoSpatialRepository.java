package com.sakhtyar.geo.infrastructure;

import com.sakhtyar.geo.application.GeoInputPolicy;
import com.sakhtyar.geo.domain.NearbyProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/**
 * Proximity lookup with an SQL-side case ACL and REAL-only boundary.
 * ST_DWithin on geography(Point,4326) retains the existing V4 GiST index.
 * Do not materialize unauthorized rows and filter them afterward.
 */
@Repository
public class GeoSpatialRepository {
    private static final int MAX_RESULTS = 50;
    private final JdbcClient jdbcClient;

    public GeoSpatialRepository(JdbcClient jdbcClient) { this.jdbcClient = jdbcClient; }

    // Package-visible for deterministic SQL-shape tests (no live database required).
    static final String NEARBY_SQL = """
            SELECT p.case_id, c.title AS case_title, p.address, p.latitude, p.longitude,
                   ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_meters
            FROM property p
            JOIN construction_case c ON c.id = p.case_id
            WHERE c.data_scope = 'REAL' AND p.data_scope = 'REAL'
              AND c.test_workspace_id IS NULL AND p.test_workspace_id IS NULL
              AND p.location IS NOT NULL
              AND (
                  :isAdmin = true OR lower(c.created_by) = lower(:actor)
                  OR EXISTS (
                      SELECT 1 FROM construction_case_access m
                      WHERE m.case_id = c.id AND m.active = true
                        AND m.data_scope = 'REAL' AND m.test_workspace_id IS NULL
                        AND lower(m.username) = lower(:actor)
                  )
              )
              AND ST_DWithin(
                  p.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius
              )
            ORDER BY distance_meters ASC, p.case_id ASC
            LIMIT :limit
            """;

    public List<NearbyProperty> findNearbyProperties(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank() ||
                auth.getAuthorities() == null ||
                auth.getAuthorities().stream().anyMatch(x -> "ROLE_TESTER".equals(x.getAuthority())) ||
                auth.getAuthorities().stream().noneMatch(x -> "CASE_READ".equals(x.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Geo access denied.");
        }
        try { GeoInputPolicy.point(latitude, longitude); GeoInputPolicy.radius(radiusMeters); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
        return jdbcClient.sql(NEARBY_SQL)
                .param("lat", latitude.doubleValue())
                .param("lng", longitude.doubleValue())
                .param("radius", radiusMeters)
                .param("limit", MAX_RESULTS)
                .param("actor", auth.getName())
                .param("isAdmin", admin)
                .query((rs, rowNum) -> new NearbyProperty(
                        rs.getObject("case_id", UUID.class),
                        rs.getString("case_title"),
                        rs.getString("address"),
                        rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"),
                        rs.getDouble("distance_meters")
                )).list();
    }
}
