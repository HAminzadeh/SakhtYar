package com.sakhtyar.quality;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fail-closed, opt-in safeguard for deployments using a restricted runtime DB principal. */
@Component
public final class T10RuntimeRoleGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final boolean enforce;

    public T10RuntimeRoleGuard(JdbcTemplate jdbc,
            @Value("${app.quality.t10.require-restricted-runtime:false}") boolean enforce) {
        this.jdbc = jdbc;
        this.enforce = enforce;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enforce) return;
        Boolean safe = jdbc.queryForObject("""
            SELECT NOT rolsuper AND NOT rolbypassrls
            FROM pg_catalog.pg_roles
            WHERE rolname = current_user
            """, Boolean.class);
        if (!Boolean.TRUE.equals(safe)) {
            throw new IllegalStateException(
                "T10 restricted runtime enforcement: application DB role is privileged or unknown");
        }
    }
}
