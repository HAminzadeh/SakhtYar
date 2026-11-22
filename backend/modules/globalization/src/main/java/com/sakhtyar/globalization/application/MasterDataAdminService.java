package com.sakhtyar.globalization.application;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MasterDataAdminService {
    private static final Logger log = LoggerFactory.getLogger(MasterDataAdminService.class);
    private static final String MARKER = "MASTER-DATA-IMPORT";

    private final GeoNamesImporter importer;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> stage = new AtomicReference<>("IDLE");
    private final AtomicReference<String> mode = new AtomicReference<>("quick");
    private final AtomicReference<String> lastError = new AtomicReference<>(null);
    private final AtomicReference<Instant> startedAt = new AtomicReference<>(null);
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>(null);

    public MasterDataAdminService(
            GeoNamesImporter importer,
            JdbcTemplate jdbc
    ) {
        this.importer = importer;
        this.jdbc = jdbc;
    }

    public Map<String, Object> startGeoNamesImport(String requestedMode) {
        String normalizedMode = "full".equalsIgnoreCase(requestedMode)
                ? "full"
                : "quick";

        if (!running.compareAndSet(false, true)) {
            return Map.of(
                    "started", false,
                    "running", true,
                    "mode", mode.get(),
                    "stage", stage.get()
            );
        }

        mode.set(normalizedMode);
        stage.set("STARTING");
        lastError.set(null);
        startedAt.set(Instant.now());
        finishedAt.set(null);

        Thread.startVirtualThread(() -> {
            try {
                log.info(
                        "{} event=ASYNC_START mode={}",
                        MARKER,
                        normalizedMode
                );

                importer.importAll(
                        normalizedMode,
                        currentStage -> {
                            stage.set(currentStage);
                            log.info(
                                    "{} event=STAGE mode={} stage={}",
                                    MARKER,
                                    normalizedMode,
                                    currentStage
                            );
                        }
                );
            } catch (Exception ex) {
                String message = rootMessage(ex);
                lastError.set(message);
                stage.set("FAILED");
                log.error(
                        "{} event=ASYNC_FAILED mode={} stage={} error={}",
                        MARKER,
                        normalizedMode,
                        stage.get(),
                        message,
                        ex
                );
            } finally {
                finishedAt.set(Instant.now());
                if (!"FAILED".equals(stage.get())) {
                    stage.set("COMPLETED");
                }
                running.set(false);
                log.info(
                        "{} event=ASYNC_FINISH mode={} stage={}",
                        MARKER,
                        normalizedMode,
                        stage.get()
                );
            }
        });

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("started", true);
        response.put("running", true);
        response.put("mode", normalizedMode);
        response.put("stage", "STARTING");
        response.put("startedAt", startedAt.get());
        return response;
    }

    public Map<String, Object> status() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("running", running.get());
        result.put("stage", stage.get());
        result.put("mode", mode.get());
        result.put("lastError", lastError.get());
        result.put("startedAt", startedAt.get());
        result.put("finishedAt", finishedAt.get());

        result.put("countryCount", count("global_country"));
        result.put("divisionCount", count("global_administrative_division"));
        result.put("cityCount", count("global_city"));
        result.put("currencyCount", count("global_currency"));

        List<Map<String, Object>> imports = jdbc.query("""
            select
              dataset,
              source_url,
              status,
              record_count,
              started_at,
              finished_at,
              error_message
            from global_master_data_import
            order by started_at desc
            limit 20
            """,
            (rs, rowNum) -> {
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("dataset", rs.getString("dataset"));
                item.put("sourceUrl", rs.getString("source_url"));
                item.put("status", rs.getString("status"));
                item.put("recordCount", rs.getLong("record_count"));
                item.put(
                        "startedAt",
                        rs.getTimestamp("started_at") == null
                                ? null
                                : rs.getTimestamp("started_at").toInstant()
                );
                item.put(
                        "finishedAt",
                        rs.getTimestamp("finished_at") == null
                                ? null
                                : rs.getTimestamp("finished_at").toInstant()
                );
                item.put("errorMessage", rs.getString("error_message"));
                return item;
            }
        );

        result.put("recentImports", imports);
        return result;
    }

    private long count(String table) {
        Long value = jdbc.queryForObject(
                "select count(*) from " + table,
                Long.class
        );
        return value == null ? 0L : value;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}