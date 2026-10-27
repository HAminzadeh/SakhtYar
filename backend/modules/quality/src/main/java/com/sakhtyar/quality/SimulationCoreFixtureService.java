package com.sakhtyar.quality;

import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * On-demand, fail-closed provisioner for the limited TEST construction-case fixture.
 *
 * The application's privileged connection ONLY provisions an isolated PostgreSQL
 * LOGIN role. All fixture SQL executes on a separate authenticated connection as
 * that restricted role. Credentials are generated in memory for every invocation.
 *
 * This is NOT general-purpose TEST isolation for SakhtYar's other 23 workflows.
 */
@Service
public final class SimulationCoreFixtureService {
    private static final List<String> CORE = List.of(
        "construction_case", "property", "property_owner", "case_document");
    private static final SecureRandom RNG = new SecureRandom();
    private static final long ADVISORY_LOCK_A = 81021930L;
    private final DataSource dataSource;

    public SimulationCoreFixtureService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    static String role(UUID workspace) {
        return "sakhtyar_tws_" + workspace.toString().replace("-", "");
    }
    static String policy(UUID workspace) {
        return "sakhtyar_tws_policy_" + workspace.toString().replace("-", "");
    }
    static String quoted(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
    private static void update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            for (int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]);
            stmt.executeUpdate();
        }
    }
    private static long count(Connection c,String sql,Object... args) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            for (int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]);
            try (ResultSet r=stmt.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    private static String oneString(Connection c,String sql,Object... args) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            for (int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]);
            try (ResultSet r=stmt.executeQuery()) { return r.next()?r.getString(1):null; }
        }
    }
    private static void setScope(Connection c,String scope,UUID workspace) throws SQLException {
        try (PreparedStatement stmt=c.prepareStatement("select set_config('app.sakhtyar_scope', ?, true), set_config('app.sakhtyar_workspace', ?, true)")) {
            stmt.setString(1,scope);
            stmt.setString(2,workspace==null?"":workspace.toString());
            stmt.executeQuery().close();
        }
    }
    private static boolean isSuperuser(Connection c) throws SQLException {
        return count(c,"select count(*) from pg_roles where rolname=current_user and rolsuper") == 1L;
    }
    private static boolean hasCorePolicy(Connection c) throws SQLException {
        long guarded = count(c,"""
            select count(*) from pg_class rel join pg_namespace ns on ns.oid=rel.relnamespace
            where ns.nspname='public' and rel.relname in
             ('construction_case','property','property_owner','case_document')
              and rel.relrowsecurity and rel.relforcerowsecurity
              and exists (select 1 from pg_policies p where p.schemaname='public'
                and p.tablename=rel.relname and p.policyname='sakhtyar_core_scope_policy')
              and exists (select 1 from pg_trigger t where t.tgrelid=rel.oid
                and t.tgname='trg_sakhtyar_core_scope' and not t.tgisinternal)
            """);
        return guarded==CORE.size();
    }
    private static boolean hasFixtureTable(Connection c) throws SQLException {
        return count(c,"select count(*) from pg_class where oid=to_regclass('public.simulation_core_fixture')") == 1L;
    }
    private static void ensureWorkspace(Connection c,UUID workspace) throws SQLException {
        if(count(c,"select count(*) from public.simulation_workspace where id=? and state='ACTIVE'",workspace)!=1L)
            throw new IllegalStateException("TEST workspace is missing or inactive");
    }
    private static boolean safeExistingRole(Connection c,String r) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
            select rolcanlogin, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb, rolinherit,
                   exists(select 1 from pg_auth_members m where m.member=pg_roles.oid) as has_memberships,
                   rolreplication,
                   exists(select 1 from pg_class c where c.relowner=pg_roles.oid
                     and c.relname in ('construction_case','property','property_owner',
                                       'case_document','simulation_core_fixture')) as owns_protected_table,
                   exists(select 1 from pg_namespace n where n.nspname='public'
                     and n.nspowner=pg_roles.oid) as owns_public_schema,
                   exists(select 1 from pg_database d where d.datname=current_database()
                     and d.datdba=pg_roles.oid) as owns_database
            from pg_roles where rolname=?
            """)) {
            p.setString(1,r);
            try(ResultSet rs=p.executeQuery()) {
                if(!rs.next()) return false;
                if(!rs.getBoolean(1)||rs.getBoolean(2)||rs.getBoolean(3)||rs.getBoolean(4)||
                    rs.getBoolean(5)||rs.getBoolean(6)||rs.getBoolean(7)||rs.getBoolean(8)||
                    rs.getBoolean(9)||rs.getBoolean(10)||rs.getBoolean(11))
                    throw new IllegalStateException("Pre-existing TEST role has unsafe privileges or memberships");
                return true;
            }
        }
    }
    private static String temporaryPassword() {
        byte[] bytes=new byte[48]; RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static Connection provision(Connection admin, UUID workspace) throws SQLException {
        if(!isSuperuser(admin))
            throw new IllegalStateException("Admin bootstrap connection lacks CREATE POLICY/ROLE privileges");
        if(!hasCorePolicy(admin)||!hasFixtureTable(admin))
            throw new IllegalStateException("V60 core RLS policies and TEST fixture table must exist before provisioning");
        ensureWorkspace(admin,workspace);
        String username=role(workspace), userIdent=quoted(username), pol=quoted(policy(workspace));
        // Serialize password rotation and LOGIN across backend instances with a DB advisory lock.
        try(PreparedStatement lock=admin.prepareStatement("select pg_advisory_lock(?,?)")) {
            lock.setInt(1,(int)ADVISORY_LOCK_A);
            lock.setInt(2,workspace.hashCode());
            lock.executeQuery().close();
        }
        try {
            boolean exists=safeExistingRole(admin,username);
            String pass=temporaryPassword();
            try(Statement s=admin.createStatement()) {
                if(!exists) s.execute("CREATE ROLE "+userIdent+
                    " LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 5 PASSWORD "+literal(pass));
                else s.execute("ALTER ROLE "+userIdent+" CONNECTION LIMIT 5 PASSWORD "+literal(pass));
                String dbName=oneString(admin,"select current_database()");
                s.execute("GRANT CONNECT ON DATABASE "+quoted(dbName)+" TO "+userIdent);
                s.execute("GRANT USAGE ON SCHEMA public TO "+userIdent);
                s.execute("GRANT SELECT(id,state) ON public.simulation_workspace TO "+userIdent);
                s.execute("GRANT SELECT,INSERT ON public.simulation_core_fixture TO "+userIdent);
                for(String table:CORE) {
                    String tbl=quoted(table);
                    s.execute("GRANT SELECT"+("case_document".equals(table)?"":",INSERT")+
                        " ON public."+tbl+" TO "+userIdent);
                    // A RESTRICTIVE policy prevents TEST login users from selecting REAL
                    // rows even if they try manipulating the session GUCs themselves.
                    String prev=oneString(admin,"select permissive || ':' || roles::text || ':' || coalesce(qual,'') from pg_policies where schemaname='public' and tablename=? and policyname=?",table,policy(workspace));
                    if(prev!=null && (!prev.startsWith("RESTRICTIVE:") || !prev.contains(username) || !prev.contains(workspace.toString())))
                        throw new IllegalStateException("Existing workspace RLS policy differs from safe template");
                    if(prev==null)
                        s.execute("CREATE POLICY "+pol+" ON public."+tbl+
                            " AS RESTRICTIVE FOR ALL TO "+userIdent+
                            " USING (data_scope='TEST' AND test_workspace_id="+literal(workspace.toString())+"::uuid)"+
                            " WITH CHECK (data_scope='TEST' AND test_workspace_id="+literal(workspace.toString())+"::uuid)");
                }
                // The fixture metadata is also partitioned by immutable workspace ID.
                s.execute("ALTER TABLE public.simulation_core_fixture ENABLE ROW LEVEL SECURITY");
                s.execute("ALTER TABLE public.simulation_core_fixture FORCE ROW LEVEL SECURITY");
                String fixturePrev=oneString(admin,"select permissive || ':' || roles::text || ':' || coalesce(qual,'') from pg_policies where schemaname='public' and tablename='simulation_core_fixture' and policyname=?",policy(workspace));
                if(fixturePrev!=null && (!fixturePrev.startsWith("PERMISSIVE:") || !fixturePrev.contains(username) || !fixturePrev.contains(workspace.toString())))
                    throw new IllegalStateException("Existing fixture metadata policy differs from safe template");
                if(fixturePrev==null)
                    s.execute("CREATE POLICY "+pol+" ON public.simulation_core_fixture"+
                        " AS PERMISSIVE FOR ALL TO "+userIdent+
                        " USING (workspace_id="+literal(workspace.toString())+"::uuid)"+
                        " WITH CHECK (workspace_id="+literal(workspace.toString())+"::uuid)");
            }
            // Obtain a PHYSICALLY DISTINCT authentication. SET ROLE on the superuser
            // pool is deliberately prohibited for this TEST fixture.
            String url=admin.getMetaData().getURL();
            if(url==null||!url.startsWith("jdbc:postgresql:")||url.matches("(?i).*([?&](?:user|password)=).*"))
                throw new IllegalStateException("Unsafe admin JDBC connection URL");
            Properties p=new Properties();p.setProperty("user",username);p.setProperty("password",pass);
            Connection restricted=DriverManager.getConnection(url,p);
            String actual=oneString(restricted,"select current_user");
            if(!username.equals(actual)||count(restricted,
                    "select count(*) from pg_roles where rolname=current_user and not rolsuper and not rolbypassrls and not rolinherit and not rolcreaterole")!=1L) {
                restricted.close();
                throw new IllegalStateException("Separate TEST connection is not actually restricted");
            }
            return restricted;
        } finally {
            try(PreparedStatement unlock=admin.prepareStatement("select pg_advisory_unlock(?,?)")) {
                unlock.setInt(1,(int)ADVISORY_LOCK_A);unlock.setInt(2,workspace.hashCode());
                unlock.executeQuery().close();
            }
        }
    }
    private Connection restrictedConnection(UUID workspace) throws SQLException {
        try(Connection admin=dataSource.getConnection()) { return provision(admin,workspace); }
    }
    public Map<String,Object> readiness() {
        Map<String,Object> r=new LinkedHashMap<>();
        List<String> blockers=new ArrayList<>();
        String adminRole="unavailable";
        boolean bootstrap=false,policy=false,table=false;
        try(Connection admin=dataSource.getConnection()) {
            adminRole=oneString(admin,"select current_user");
            bootstrap=isSuperuser(admin);policy=hasCorePolicy(admin);table=hasFixtureTable(admin);
            if(!bootstrap)blockers.add("Automatic test role provisioning requires an authorized admin DB bootstrap connection");
            if(!policy)blockers.add("V60 FORCE RLS, guard triggers or core policy missing");
            if(!table)blockers.add("simulation_core_fixture table missing");
        } catch(Exception ex) { blockers.add("Database readiness query failed: "+ex.getClass().getSimpleName()); }
        boolean ready=bootstrap&&policy&&table;
        r.put("coreFixturePermitted",ready);
        r.put("automaticTestRoleProvisioning",bootstrap);
        r.put("runtimeDbRoleNonBypass",ready); // reports the dedicated generated TEST login, not admin login
        r.put("adminBootstrapDbUser",adminRole);
        r.put("runtimeDbUser","sakhtyar_tws_<workspace-id> (created on demand)");
        r.put("corePolicyCoverage",policy);
        r.put("migration60StructureAvailable",table);
        r.put("fullApplicationIsolationVerified",false);
        r.put("externalSideEffectsGloballyGuarded",false);
        r.put("businessE2eCatalogEnabled",false);
        r.put("blockers",blockers);
        r.put("note","On explicit TEST action, a per-workspace non-bypass PostgreSQL LOGIN role is created/reused automatically, credentials are ephemeral. Only limited core fixture uses this restricted connection. Other E2E workflows remain gated.");
        return r;
    }
    private static Map<String,Object> mapFixture(ResultSet rs) throws SQLException {
        Map<String,Object> m=new LinkedHashMap<>();
        for(String name:List.of("id","case_id","property_id","owner_a_id","owner_b_id","workspace_id","request_key","created_by","outcome","created_at")) {
            try { m.put(name,rs.getObject(name)); } catch(SQLException ignored) { /* optional computed field */ }
        }
        return m;
    }
    public Map<String,Object> create(UUID workspace,String key,String actor) {
        if(workspace==null||key==null||!key.matches("[A-Za-z0-9_-]{8,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid TEST workspace or request key");
        try(Connection c=restrictedConnection(workspace)) {
            c.setAutoCommit(false);
            try {
                setScope(c,"TEST",workspace);
                Map<String,Object> existing=fetchExisting(c,workspace,key);
                if(existing!=null) { c.commit(); return existing; }
                UUID caseId=UUID.randomUUID(),propId=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),f=UUID.randomUUID();
                String code="SYT-"+caseId.toString().replace("-","").toUpperCase(Locale.ROOT);
                update(c,"""
                    insert into public.construction_case
                    (id,title,status,description,city,district,address,land_area_m2,created_by,
                     created_at,updated_at,project_code,data_scope,test_workspace_id)
                    values (?,?,'DRAFT',?,'TEST_CITY',null,?,300.00,?,now(),now(),?,'TEST',?)
                    """,caseId,"SakhtYar synthetic TEST case","Synthetic fixture; not a real property",
                     "TEST ADDRESS ONLY - NOT REAL",actor,code,workspace);
                update(c,"""
                    insert into public.property
                    (id,case_id,province,city,address,land_area_m2,created_at,updated_at,data_scope,test_workspace_id)
                    values (?,?,'TEST_PROVINCE','TEST_CITY','TEST ADDRESS ONLY - NOT REAL',300.00,
                            now(),now(),'TEST',?)
                    """,propId,caseId,workspace);
                update(c,"""
                    insert into public.property_owner
                    (id,property_id,first_name,last_name,ownership_numerator,ownership_denominator,
                     is_primary_contact,created_at,updated_at,data_scope,test_workspace_id)
                    values (?,?,'TEST','OWNER_A',60,100,true,now(),now(),'TEST',?)
                    """,a,propId,workspace);
                update(c,"""
                    insert into public.property_owner
                    (id,property_id,first_name,last_name,ownership_numerator,ownership_denominator,
                     is_primary_contact,created_at,updated_at,data_scope,test_workspace_id)
                    values (?,?,'TEST','OWNER_B',40,100,false,now(),now(),'TEST',?)
                    """,b,propId,workspace);
                if(count(c,"select count(*) from public.property_owner where property_id=?",propId)!=2L ||
                   count(c,"select coalesce(sum(ownership_numerator),0) from public.property_owner where property_id=?",propId)!=100L)
                    throw new IllegalStateException("TEST fixture owners and shares assertion failed");
                // A TEST-only role must NEVER be able to select REAL rows, even if
                // an attacker changes the session's custom GUC to REAL.
                setScope(c,"REAL",null);
                if(count(c,"select count(*) from public.construction_case where id=?",caseId)!=0L ||
                   count(c,"select count(*) from public.construction_case where data_scope='REAL'")!=0L ||
                   count(c,"select count(*) from public.construction_case where data_scope='TEST' and test_workspace_id<>?",workspace)!=0L)
                    throw new IllegalStateException("SECURITY FAILURE: TEST login can read REAL core records");
                setScope(c,"TEST",workspace);
                update(c,"""
                    insert into public.simulation_core_fixture
                    (id,workspace_id,request_key,case_id,property_id,owner_a_id,owner_b_id,created_by)
                    values (?,?,?,?,?,?,?,?)
                    """,f,workspace,key,caseId,propId,a,b,actor);
                Map<String,Object> row=fetchExisting(c,workspace,key);
                if(row==null)throw new IllegalStateException("Committed fixture metadata cannot be read");
                c.commit();return row;
            } catch(Exception ex) { c.rollback();throw ex; }
        } catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Automated restricted TEST role/fixture failed (no REAL rows modified): "+ex.getClass().getSimpleName()); }
    }
    private static Map<String,Object> fetchExisting(Connection c,UUID workspace,String key) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
            select f.*,c.project_code,c.title,c.status,
             (select count(*) from public.property_owner where property_id=f.property_id) as owner_count
            from public.simulation_core_fixture f join public.construction_case c on c.id=f.case_id
            where f.workspace_id=? and f.request_key=?
            """)) {
            p.setObject(1,workspace);p.setString(2,key);
            try(ResultSet rs=p.executeQuery()) {
                if(!rs.next()) return null;
                Map<String,Object> m=mapFixture(rs);
                m.put("project_code",rs.getString("project_code"));
                m.put("title",rs.getString("title"));
                m.put("status",rs.getString("status"));
                m.put("owner_count",rs.getLong("owner_count"));
                return m;
            }
        }
    }
    public List<Map<String,Object>> list(UUID workspace) {
        if(workspace==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"workspaceId required");
        // Reading the admin evidence view must never provision/rotate a role. Only
        // explicit TEST writes invoke the privileged DDL bootstrap path.
        try(Connection c=dataSource.getConnection()) {
            List<Map<String,Object>> out=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("""
                select f.*,c.project_code,c.title,c.status,
                 (select count(*) from public.property_owner o where o.property_id=f.property_id
                   and o.data_scope='TEST' and o.test_workspace_id=?) as owner_count
                from public.simulation_core_fixture f join public.construction_case c on c.id=f.case_id
                where f.workspace_id=? and c.data_scope='TEST' and c.test_workspace_id=?
                order by f.created_at desc limit 100
                """)) {
                p.setObject(1,workspace);p.setObject(2,workspace);p.setObject(3,workspace);
                try(ResultSet rs=p.executeQuery()) {
                    while(rs.next()) {
                        Map<String,Object> row=mapFixture(rs);
                        row.put("project_code",rs.getString("project_code"));
                        row.put("title",rs.getString("title"));
                        row.put("status",rs.getString("status"));
                        row.put("owner_count",rs.getLong("owner_count"));
                        out.add(row);
                    }
                }
            }
            return out;
        } catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Unable to list isolated TEST fixture evidence: "+ex.getClass().getSimpleName()); }
    }
}
