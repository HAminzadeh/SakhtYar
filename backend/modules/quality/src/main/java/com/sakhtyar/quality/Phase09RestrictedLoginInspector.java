package com.sakhtyar.quality;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Read-only physical login observer using independently supplied TEST credentials.
 * No SET ROLE, CREATE ROLE, GRANT, DDL, fixture write, schema change or application DataSource fallback.
 * A zero-row result does NOT prove cross-workspace isolation without a positive baseline.
 */
@Component
public final class Phase09RestrictedLoginInspector {
    private static final String[] CORE={"construction_case","property","property_owner","case_document"};
    private final String url,username,password;
    public Phase09RestrictedLoginInspector(@Value("${SAKHTYAR_E2E_PROBE_JDBC_URL:}") String url,
            @Value("${SAKHTYAR_E2E_PROBE_USERNAME:}") String username,
            @Value("${SAKHTYAR_E2E_PROBE_PASSWORD:}") String password) {
        this.url=url==null?"":url;this.username=username==null?"":username;
        this.password=password==null?"":password;
    }
    public Map<String,Object> inspect(UUID workspace) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("kind","DEDICATED_PHYSICAL_TEST_LOGIN_READ_ONLY_OBSERVATION");
        out.put("state","BLOCKED");out.put("physicalRestrictedLogin",false);
        out.put("restrictedRolePrivilegesVerified",false);
        out.put("crossWorkspaceRealInvisibilityProven",false);
        out.put("fullApplicationIsolationVerified",false);
        if(workspace==null) { out.put("blocker","WORKSPACE_MISSING");return out; }
        String required="sakhtyar_tws_"+workspace.toString().replace("-","");
        if(url.isBlank()||username.isBlank()||password.isBlank()) {
            out.put("blocker","DEDICATED_TEST_PROBE_CREDENTIALS_NOT_CONFIGURED");return out;
        }
        if(!url.startsWith("jdbc:postgresql://") || url.matches("(?i).*[?&](user|password)=.*") ||
           !username.equals(required)) {
            out.put("blocker","UNSAFE_PROBE_ENDPOINT_OR_ROLE_MISMATCH");return out;
        }
        Properties props=new Properties();props.setProperty("user",username);props.setProperty("password",password);
        props.setProperty("connectTimeout","3");props.setProperty("socketTimeout","5");
        try(Connection connection=DriverManager.getConnection(url,props)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                String current=scalarText(connection,"select current_user");
                String session=scalarText(connection,"select session_user");
                String rowSecurity=scalarText(connection,"show row_security");
                if(!required.equals(current)||!required.equals(session)||!"on".equalsIgnoreCase(rowSecurity)) {
                    out.put("blocker","PHYSICAL_LOGIN_NOT_WORKSPACE_ROLE");return out;
                }
                long safe=scalarLong(connection,"""
                    select count(*) from pg_catalog.pg_roles
                    where rolname=current_user and rolcanlogin and not rolsuper
                      and not rolbypassrls and not rolcreaterole and not rolcreatedb
                      and not rolreplication and not rolinherit
                      and not exists(select 1 from pg_catalog.pg_auth_members m where m.member=pg_roles.oid)
                    """);
                if(safe!=1L) {out.put("blocker","RESTRICTED_ROLE_UNSAFE");return out;}
                out.put("db_current_user",current);out.put("db_session_user",session);
                out.put("row_security",rowSecurity);
                out.put("physicalRestrictedLogin",true);
                out.put("restrictedRolePrivilegesVerified",true);
                // Verify only absence of visibly forbidden rows, not non-existence in the database.
                Map<String,Long> realVisible=new LinkedHashMap<>();
                for(String table:CORE) {
                    long guarded=scalarLong(connection,"""
                        select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid=c.relnamespace
                        where n.nspname='public' and c.relname=? and c.relrowsecurity and c.relforcerowsecurity
                        """,table);
                    if(guarded!=1L) {out.put("blocker","CORE_FORCE_RLS_MISSING");return out;}
                    realVisible.put(table,scalarLong(connection,"select count(*) from public."+table+" where data_scope='REAL'"));
                }
                out.put("realRowsVisible",realVisible);
                if(realVisible.values().stream().anyMatch(n->n!=0L)) {
                    out.put("blocker","REAL_ROWS_VISIBLE_TO_TEST_ROLE");return out;
                }
                out.put("state","OBSERVED_NOT_FULLY_PROVEN");
                out.put("blocker","MISSING_INDEPENDENT_POSITIVE_CONTROL_AND_FULL_APPLICATION_SCOPE");
                return out;
            } finally {connection.rollback();}
        } catch(Exception ex) {
            out.put("blocker","READ_ONLY_PROBE_FAILED_"+ex.getClass().getSimpleName());return out;
        } finally {props.clear();}
    }
    private static String scalarText(Connection c,String sql) throws Exception {
        try(PreparedStatement s=c.prepareStatement(sql);ResultSet r=s.executeQuery()) {
            if(!r.next()) throw new IllegalStateException("Missing identity proof");return r.getString(1);
        }
    }
    private static long scalarLong(Connection c,String sql,Object... parameters) throws Exception {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            for(int i=0;i<parameters.length;i++) s.setObject(i+1,parameters[i]);
            try(ResultSet r=s.executeQuery()) {
                if(!r.next()) throw new IllegalStateException("Missing catalog proof");return r.getLong(1);
            }
        }
    }
}
