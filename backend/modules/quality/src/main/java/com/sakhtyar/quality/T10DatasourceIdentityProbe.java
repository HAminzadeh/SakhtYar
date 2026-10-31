package com.sakhtyar.quality;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Opt-in, read-only evidence obtained from actual JDBC connections. Never logs secrets. */
@Component
public final class T10DatasourceIdentityProbe implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(T10DatasourceIdentityProbe.class);
    private static final String IDENTITY_SQL = """
        SELECT current_user, session_user, r.rolsuper, r.rolbypassrls,
               r.rolcreaterole, r.rolcreatedb, r.rolcanlogin,
               (SELECT count(*) FROM pg_catalog.pg_policies
                 WHERE schemaname='public' AND tablename IN
                 ('t10_e2e_run','t10_e2e_scenario_run','t10_e2e_step'))
          FROM pg_catalog.pg_roles r WHERE r.rolname=current_user
        """;
    private final DataSource runtime;
    private final T10FixtureAdminConnectionProvider fixture;
    private final boolean enabled;
    public T10DatasourceIdentityProbe(DataSource runtime,
            T10FixtureAdminConnectionProvider fixture,
            @Value("${app.quality.t10.datasource-identity-probe:false}") boolean enabled) {
        this.runtime = runtime;
        this.fixture = fixture;
        this.enabled = enabled;
    }
    public record Identity(String currentUser, String sessionUser, boolean superuser,
                           boolean bypassRls, boolean createRole, boolean createDb,
                           boolean canLogin, int historyPolicies) {
        public boolean restricted() { return !superuser && !bypassRls && !createRole && !createDb; }
    }
    public static Identity inspect(DataSource source) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement q = c.prepareStatement(IDENTITY_SQL);
             ResultSet r = q.executeQuery()) {
            if (!r.next()) throw new SQLException("Database current_user role not found");
            Identity out = new Identity(r.getString(1), r.getString(2), r.getBoolean(3),
                    r.getBoolean(4), r.getBoolean(5), r.getBoolean(6), r.getBoolean(7), r.getInt(8));
            if (r.next()) throw new SQLException("Unexpected multiple role rows");
            return out;
        }
    }
    public static boolean distinctPrincipal(Identity runtime, Identity admin) {
        return !runtime.currentUser().equals(admin.currentUser());
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        Identity app = inspect(runtime);
        LOG.warn("T10 DB EVIDENCE runtime_user={} session_user={} restricted={} superuser={} bypassrls={} " +
                 "createrole={} createdb={} history_policy_count={}",
                 app.currentUser(), app.sessionUser(), app.restricted(), app.superuser(),
                 app.bypassRls(), app.createRole(), app.createDb(), app.historyPolicies());
        if (!fixture.isDedicated()) {
            LOG.warn("T10 DB EVIDENCE fixture_admin_dedicated=false status=BLOCKED");
            return;
        }
        Identity admin = inspect(fixture.dataSource());
        LOG.warn("T10 DB EVIDENCE fixture_admin_dedicated=true admin_user={} " +
                 "distinct_principal={} admin_superuser={} status={}",
                 admin.currentUser(), distinctPrincipal(app,admin), admin.superuser(),
                 (app.restricted() && distinctPrincipal(app,admin) && admin.superuser()) ? "PARTIAL" : "BLOCKED");
        // PARTIAL only: an identity probe cannot establish grants, end-to-end workload
        // compatibility, SQL-level scope isolation, or safe V64 deployment.
    }
}
