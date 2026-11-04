package com.sakhtyar.quality;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only core RLS probe. Requires an ALREADY AUTHENTICATED physical connection
 * as a restricted workspace LOGIN (session_user == current_user); never calls SET ROLE.
 * It does not establish full-application isolation or authorize E2E PASS.
 */
public final class Phase02RestrictedIsolationProbe {
    private static final String[] CORE={"construction_case","property","property_owner","case_document"};
    private Phase02RestrictedIsolationProbe() {}

    public record Observation(String state, String loginRole,
            Map<String,Long> ownCounts, Map<String,Long> otherCounts,
            Map<String,Long> realExposures, boolean restrictedRole,
            boolean workspacePolicyVerified, boolean baselineOfOtherWorkspaceVerified) {
        public Observation {
            ownCounts=Map.copyOf(ownCounts);
            otherCounts=Map.copyOf(otherCounts);
            realExposures=Map.copyOf(realExposures);
        }
    }

    public static Observation inspect(Connection connection, UUID workspace,
            UUID otherWorkspace, boolean otherHasRecordsAccordingToIndependentAdminAudit) throws SQLException {
        if(connection==null || workspace==null || otherWorkspace==null || workspace.equals(otherWorkspace))
            throw new IllegalArgumentException("Independent distinct workspaces required");
        if(!connection.getAutoCommit())
            throw new IllegalStateException("Provide a fresh, autocommit physical TEST connection");
        String role="sakhtyar_tws_"+workspace.toString().replace("-", "");
        boolean originallyReadOnly=connection.isReadOnly();
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try {
            String actual = scalarText(connection,"select current_user");
            String session = scalarText(connection,"select session_user");
            if(!role.equals(actual) || !role.equals(session))
                throw new SecurityException("Physical restricted TEST login was not verified");
            long restricted = scalarLong(connection,"""
                select count(*) from pg_catalog.pg_roles
                where rolname=current_user and rolcanlogin and not rolsuper
                  and not rolbypassrls and not rolcreaterole and not rolinherit
                  and not rolcreatedb and not rolreplication
                """);
            if(restricted!=1L) throw new SecurityException("Unsafe PostgreSQL TEST role");
            String policy="sakhtyar_tws_policy_"+workspace.toString().replace("-", "");
            for(String table:CORE) {
                if(scalarLong(connection,"""
                    select count(*) from pg_catalog.pg_class c
                    join pg_catalog.pg_namespace n on n.oid=c.relnamespace
                    where n.nspname='public' and c.relname=? and c.relrowsecurity
                      and c.relforcerowsecurity
                    """,table)!=1L) throw new SecurityException("RLS missing for "+table);
                if(scalarLong(connection,"""
                    select count(*) from pg_catalog.pg_policies
                    where schemaname='public' and tablename=? and policyname=?
                      and permissive='RESTRICTIVE' and ?::name = any(roles)
                      and position(? in coalesce(qual,'')) > 0
                      and position(? in coalesce(with_check,'')) > 0
                    """,table,policy,role,workspace.toString(),workspace.toString())!=1L)
                    throw new SecurityException("Workspace-specific restrictive policy missing for "+table);
            }
            Map<String,Long> own=new LinkedHashMap<>(), other=new LinkedHashMap<>(), real=new LinkedHashMap<>();
            scope(connection,"TEST",workspace);
            for(String t:CORE) own.put(t,scalarLong(connection,
                "select count(*) from public."+t+" where data_scope='TEST' and test_workspace_id=?",workspace));
            scope(connection,"TEST",otherWorkspace);
            for(String t:CORE) other.put(t,scalarLong(connection,
                "select count(*) from public."+t+" where data_scope='TEST' and test_workspace_id=?",otherWorkspace));
            scope(connection,"REAL",null);
            for(String t:CORE) real.put(t,scalarLong(connection,
                "select count(*) from public."+t+" where data_scope='REAL'"));
            boolean hidden=other.values().stream().allMatch(c->c==0L) && real.values().stream().allMatch(c->c==0L);
            boolean hasOwn=own.values().stream().anyMatch(c->c>0L);
            boolean hasOther=otherHasRecordsAccordingToIndependentAdminAudit;
            // A zero-row probe against a nonexistent other workspace is NOT complete isolation proof.
            String state=hidden && hasOwn && hasOther ? "CORE_READ_PROBE_OBSERVED" :
                hidden ? "INCONCLUSIVE_NO_CROSS_WORKSPACE_BASELINE" : "ISOLATION_VIOLATION";
            return new Observation(state,role,own,other,real,true,true,hasOther);
        } finally {
            try { connection.rollback(); } finally {
                try { connection.setAutoCommit(true); } finally { connection.setReadOnly(originallyReadOnly); }
            }
        }
    }

    private static void scope(Connection c,String scope,UUID workspace) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("select set_config('app.sakhtyar_scope', ?, true), set_config('app.sakhtyar_workspace', ?, true)")) {
            s.setString(1,scope);s.setString(2,workspace==null?"":workspace.toString());
            try(ResultSet r=s.executeQuery()) { if(!r.next()) throw new SQLException("Unable to set TEST scope"); }
        }
    }
    private static long scalarLong(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            try(ResultSet r=s.executeQuery()) {
                if(!r.next()) throw new SQLException("Missing result");
                return r.getLong(1);
            }
        }
    }
    private static String scalarText(Connection c,String sql) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql);ResultSet r=s.executeQuery()) {
            if(!r.next()) throw new SQLException("Missing result");return r.getString(1);
        }
    }
}
