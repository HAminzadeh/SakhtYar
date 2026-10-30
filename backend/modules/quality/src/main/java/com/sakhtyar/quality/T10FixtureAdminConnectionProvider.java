package com.sakhtyar.quality;

import javax.sql.DataSource;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/**
 * Explicit, opt-in administrative connection used ONLY by the limited T10 fixture
 * provisioner. Not registered as a DataSource bean to avoid replacing Spring Boot's
 * auto-configured primary application DataSource, JPA and Flyway connections.
 */
@Component
public final class T10FixtureAdminConnectionProvider {
    private final DataSource selected;
    private final boolean dedicated;

    public T10FixtureAdminConnectionProvider(DataSource applicationDataSource,
            @Value("${APP_T10_FIXTURE_ADMIN_JDBC_URL:}") String jdbcUrl,
            @Value("${APP_T10_FIXTURE_ADMIN_USERNAME:}") String username,
            @Value("${APP_T10_FIXTURE_ADMIN_PASSWORD:}") String password,
            @Value("${APP_T10_FIXTURE_ADMIN_REQUIRE_DEDICATED:false}") boolean requireDedicated) {
        Objects.requireNonNull(applicationDataSource, "applicationDataSource");
        this.dedicated = hasText(jdbcUrl) && hasText(username) && hasText(password);
        if (!dedicated && (hasText(jdbcUrl) || hasText(username) || hasText(password))) {
            throw new IllegalStateException("T10 fixture admin datasource requires all three settings");
        }
        if (requireDedicated && !dedicated) {
            throw new IllegalStateException("Dedicated T10 fixture admin datasource is required");
        }
        if (dedicated) {
            if (!jdbcUrl.startsWith("jdbc:postgresql:") || jdbcUrl.matches("(?i).*[?&](?:user|password)=.*")) {
                throw new IllegalStateException("Unsafe T10 fixture admin JDBC URL");
            }
            DriverManagerDataSource isolated = new DriverManagerDataSource();
            isolated.setUrl(jdbcUrl);
            isolated.setUsername(username);
            isolated.setPassword(password);
            this.selected = isolated;
        } else {
            // Compatibility only; remove after the dedicated-role rollout is verified.
            this.selected = applicationDataSource;
        }
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    public DataSource dataSource() { return selected; }
    public boolean isDedicated() { return dedicated; }
}