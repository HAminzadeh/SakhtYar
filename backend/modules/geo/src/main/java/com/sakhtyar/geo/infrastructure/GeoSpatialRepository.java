package com.sakhtyar.geo.infrastructure;

import com.sakhtyar.geo.domain.NearbyProperty;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GeoSpatialRepository {

    private static final int MAX_RESULTS = 50;

    private final JdbcClient jdbcClient;

    public GeoSpatialRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<NearbyProperty> findNearbyProperties(
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusMeters
    ) {
        String sql = """
                SELECT
                    p.case_id,
                    c.title AS case_title,
                    p.address,
                    p.latitude,
                    p.longitude,
                    ST_Distance(
                        p.location,
                        ST_SetSRID(
                            ST_MakePoint(:lng, :lat),
                            4326
                        )::geography
                    ) AS distance_meters
                FROM property p
                JOIN construction_case c ON c.id = p.case_id
                WHERE p.location IS NOT NULL
                  AND ST_DWithin(
                        p.location,
                        ST_SetSRID(
                            ST_MakePoint(:lng, :lat),
                            4326
                        )::geography,
                        :radius
                  )
                ORDER BY distance_meters
                LIMIT :limit
                """;

        return jdbcClient.sql(sql)
                .param("lat", latitude.doubleValue())
                .param("lng", longitude.doubleValue())
                .param("radius", radiusMeters)
                .param("limit", MAX_RESULTS)
                .query((rs, rowNum) -> new NearbyProperty(
                        rs.getObject("case_id", java.util.UUID.class),
                        rs.getString("case_title"),
                        rs.getString("address"),
                        rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"),
                        rs.getDouble("distance_meters")
                ))
                .list();
    }
}
