package com.sakhtyar.globalization.application;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MasterDataAdminService {
    private final GeoNamesImporter importer;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MasterDataAdminService(GeoNamesImporter importer, JdbcTemplate jdbc) {
        this.importer = importer;
        this.jdbc = jdbc;
    }

    public Map<String,Object> startGeoNamesImport() {
        if (!running.compareAndSet(false, true)) {
            return Map.of("started", false, "running", true);
        }

        Thread.startVirtualThread(() -> {
            try {
                importer.importAll();
            } finally {
                running.set(false);
            }
        });

        return Map.of("started", true, "running", true, "startedAt", Instant.now().toString());
    }

    public Map<String,Object> status() {
        LinkedHashMap<String,Object> result = new LinkedHashMap<>();
        result.put("running", running.get());
        result.put("countryCount", count("global_country"));
        result.put("divisionCount", count("global_administrative_division"));
        result.put("cityCount", count("global_city"));
        result.put("currencyCount", count("global_currency"));

        List<Map<String,Object>> imports = jdbc.query("""
            select dataset,source_url,status,record_count,started_at,finished_at,error_message
            from global_master_data_import
            order by started_at desc
            limit 20
            """, (rs, rowNum) -> {
                LinkedHashMap<String,Object> item = new LinkedHashMap<>();
                item.put("dataset", rs.getString("dataset"));
                item.put("sourceUrl", rs.getString("source_url"));
                item.put("status", rs.getString("status"));
                item.put("recordCount", rs.getLong("record_count"));
                item.put("startedAt", rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant());
                item.put("finishedAt", rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant());
                item.put("errorMessage", rs.getString("error_message"));
                return item;
            });

        result.put("recentImports", imports);
        return result;
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0L : value;
    }
}