package com.sakhtyar.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Read-only history readiness check. Never invokes Flyway or modifies the database. */
public final class DatabaseMigrationPreflightInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Pattern NAME = Pattern.compile("V([1-9][0-9]*)__[A-Za-z0-9_]+\\.sql");
    private static final String NEXT = " Local startup migration is enabled only for the local profile. "
            + "Inspect the private MigrationLifecycle report or use the explicit approved operator workflow. "
            + "Do not enable automatic Flyway or Hibernate schema update.";

    record HistoryRow(String version, String script, String type, boolean success, Integer checksum) {}

    static Map<Integer, String> inventory(List<String> files) {
        Map<Integer, String> result = new TreeMap<>();
        for (String filename : files) {
            var m = NAME.matcher(filename);
            if (!m.matches()) throw new IllegalStateException("Invalid migration filename." + NEXT);
            int version = Integer.parseInt(m.group(1));
            if (result.putIfAbsent(version, filename) != null)
                throw new IllegalStateException("Duplicate migration version V" + version + NEXT);
        }
        if (result.isEmpty()) throw new IllegalStateException("No migration resources found." + NEXT);
        for (int n = 1; n <= result.size(); n++) {
            if (!result.containsKey(n)) throw new IllegalStateException("Missing migration resource V" + n + NEXT);
        }
        return result;
    }

    static void verify(Map<Integer, String> expected, List<HistoryRow> rows) {
        int n = 1;
        for (HistoryRow row : rows) {
            if (!row.success() || !"SQL".equals(row.type()) || row.checksum() == null
                    || !Integer.toString(n).equals(row.version())
                    || !row.script().equals(expected.get(n))) {
                throw new IllegalStateException("Migration history mismatch at installation position " + n + NEXT);
            }
            n++;
        }
        if (rows.size() != expected.size()) {
            throw new IllegalStateException("DATABASE_MIGRATION_PENDING: applied V" + rows.size()
                    + ", source requires V" + expected.size() + NEXT);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment env = context.getEnvironment();
        // Existing profiles intentionally disable Flyway. Leave explicitly managed Flyway paths alone.
        if (env.getProperty("spring.flyway.enabled", Boolean.class, true)) return;
        if (!env.matchesProfiles("local", "development", "production")) return;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver(context)
                    .getResources("classpath*:db/migration/V*__*.sql");
            List<String> files = new ArrayList<>();
            for (Resource resource : resources) files.add(resource.getFilename());
            Map<Integer, String> expected = inventory(files);
            String url = env.getRequiredProperty("spring.datasource.url");
            Properties props = new Properties();
            props.setProperty("user", env.getRequiredProperty("spring.datasource.username"));
            props.setProperty("password", env.getRequiredProperty("spring.datasource.password"));
            props.setProperty("connectTimeout", "5");
            props.setProperty("socketTimeout", "10");
            List<HistoryRow> rows = new ArrayList<>();
            try (var conn = DriverManager.getConnection(url, props)) {
                conn.setReadOnly(true);
                conn.setAutoCommit(false);
                try (var statement = conn.prepareStatement("SELECT version, script, type, success, checksum "
                        + "FROM public.flyway_schema_history ORDER BY installed_rank")) {
                    statement.setQueryTimeout(10);
                    try (var rs = statement.executeQuery()) {
                        while (rs.next()) rows.add(new HistoryRow(rs.getString(1), rs.getString(2),
                                rs.getString(3), rs.getBoolean(4), rs.getObject(5, Integer.class)));
                    }
                } finally {
                    conn.rollback();
                }
            }
            verify(expected, rows);
            // History readiness is not Flyway checksum validation or Hibernate validation.
            System.out.println("[SakhtYar Migration] History current through V" + expected.size()
                    + "; Hibernate validation follows.");
        } catch (SQLException e) {
            // Never leak connection strings, credentials or SQL row values into startup logs.
            throw new IllegalStateException("Database migration history is not readable (SQLSTATE="
                    + e.getSQLState() + "). Verify runtime SELECT privilege and connectivity." + NEXT);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not enumerate migration resources." + NEXT);
        }
    }
}
