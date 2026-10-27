package com.sakhtyar.quality;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * T05 proof of core-domain DB isolation. Admin-only caller is enforced by
 * SimulationController. Does NOT invoke document storage, notifications, network
 * integrations, or claim completion of the E2E business catalog.
 */
@Service
public class SimulationCoreFixtureService {
    private static final List<String> CORE = List.of(
        "construction_case", "property", "property_owner", "case_document");
    private final JdbcTemplate db;
    private final TransactionTemplate tx;

    public SimulationCoreFixtureService(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db=db;
        this.tx=new TransactionTemplate(manager);
    }

    private void context(String scope, UUID workspaceId) {
        db.queryForObject("select set_config('app.sakhtyar_scope',?,true)",String.class,scope);
        db.queryForObject("select set_config('app.sakhtyar_workspace',?,true)",String.class,
            workspaceId == null ? "" : workspaceId.toString());
    }

    public Map<String,Object> readiness() {
        boolean roleSafe=Boolean.TRUE.equals(db.queryForObject("""
            select not rolsuper and not rolbypassrls from pg_roles where rolname=current_user
            """,Boolean.class));
        List<Map<String,Object>> policies=db.queryForList("""
            select c.relname as table_name,c.relrowsecurity as enabled,c.relforcerowsecurity as forced,
              exists (select 1 from pg_policies p where p.schemaname='public'
                and p.tablename=c.relname and p.policyname='sakhtyar_core_scope_policy') as has_policy,
              exists (select 1 from pg_trigger t where t.tgrelid=c.oid
                and t.tgname='trg_sakhtyar_core_scope' and not t.tgisinternal) as has_guard
            from pg_class c join pg_namespace n on n.oid=c.relnamespace
            where n.nspname='public' and c.relname in ('construction_case','property','property_owner','case_document')
            order by c.relname
            """);
        boolean policiesOk=policies.size()==CORE.size() && policies.stream().allMatch(row->
           Boolean.TRUE.equals(row.get("enabled")) && Boolean.TRUE.equals(row.get("forced")) &&
           Boolean.TRUE.equals(row.get("has_policy")) && Boolean.TRUE.equals(row.get("has_guard")));
        boolean fixturesTable=Boolean.TRUE.equals(db.queryForObject(
            "select to_regclass('public.simulation_core_fixture') is not null",Boolean.class));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("coreFixturePermitted",roleSafe && policiesOk && fixturesTable);
        result.put("runtimeDbRoleNonBypass",roleSafe);
        result.put("corePolicyTables",policies);
        result.put("corePolicyCoverage",policiesOk);
        result.put("migration60StructureAvailable",fixturesTable);
        result.put("fullApplicationIsolationVerified",false);
        result.put("externalSideEffectsGloballyGuarded",false);
        result.put("businessE2eCatalogEnabled",false);
        result.put("note","Scoped core fixture only. Other 20+ modules and global profile switch are NOT isolated.");
        return result;
    }

    private void ensureReady() {
        if (!Boolean.TRUE.equals(readiness().get("coreFixturePermitted"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Core fixture disabled: non-bypass PostgreSQL role and V60 forced RLS/guards required");
        }
    }

    public Map<String,Object> create(UUID workspaceId,String key,String actor) {
        if (key==null || !key.matches("[A-Za-z0-9_-]{8,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid fixture idempotency key");
        ensureReady();
        return Objects.requireNonNull(tx.execute(status -> {
            // Lock workspace to serialize retries with the same idempotency key.
            List<Map<String,Object>> found=db.queryForList(
                "select id from simulation_workspace where id=? and state='ACTIVE' for update",workspaceId);
            if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active workspace not found");
            List<Map<String,Object>> existing=db.queryForList(
                "select * from simulation_core_fixture where workspace_id=? and request_key=?",workspaceId,key);
            if (!existing.isEmpty()) return existing.get(0);
            context("TEST",workspaceId);
            UUID caseId=UUID.randomUUID(),propertyId=UUID.randomUUID();
            UUID ownerA=UUID.randomUUID(),ownerB=UUID.randomUUID(),fixtureId=UUID.randomUUID();
            String projectCode="SYT-"+caseId.toString().replace("-","").toUpperCase(Locale.ROOT);
            db.update("""
                insert into construction_case
                (id,title,status,description,city,district,address,land_area_m2,created_by,
                 created_at,updated_at,project_code,data_scope,test_workspace_id)
                values (?,?,'DRAFT',?,'TEST_CITY',null,?,300.00,?,now(),now(),?,'TEST',?)
                """,caseId,"SakhtYar T05 synthetic participation case",
                "Synthetic fixture; never an actual property or legal entitlement",
                "TEST ADDRESS ONLY - NOT REAL",actor,projectCode,workspaceId);
            db.update("""
                insert into property
                (id,case_id,province,city,address,land_area_m2,created_at,updated_at,
                 data_scope,test_workspace_id)
                values (?,?,'TEST_PROVINCE','TEST_CITY','TEST ADDRESS ONLY - NOT REAL',300.00,
                        now(),now(),'TEST',?)
                """,propertyId,caseId,workspaceId);
            db.update("""
                insert into property_owner
                (id,property_id,first_name,last_name,ownership_numerator,ownership_denominator,
                 is_primary_contact,created_at,updated_at,data_scope,test_workspace_id)
                values (?,?,?, ?,60,100,true,now(),now(),'TEST',?)
                """,ownerA,propertyId,"TEST","OWNER_A",workspaceId);
            db.update("""
                insert into property_owner
                (id,property_id,first_name,last_name,ownership_numerator,ownership_denominator,
                 is_primary_contact,created_at,updated_at,data_scope,test_workspace_id)
                values (?,?,?, ?,40,100,false,now(),now(),'TEST',?)
                """,ownerB,propertyId,"TEST","OWNER_B",workspaceId);
            // Assert all three foreign-key layers and two synthetic ownership shares.
            Long visible=db.queryForObject("""
                select count(*) from property_owner o join property p on p.id=o.property_id
                join construction_case c on c.id=p.case_id
                where c.id=? and c.data_scope='TEST' and p.data_scope='TEST' and o.data_scope='TEST'
                  and c.test_workspace_id=? and p.test_workspace_id=? and o.test_workspace_id=?
                """,Long.class,caseId,workspaceId,workspaceId,workspaceId);
            Integer ownership=db.queryForObject("""
                select sum(ownership_numerator) from property_owner where property_id=?
                """,Integer.class,propertyId);
            if(!Objects.equals(visible,2L) || !Objects.equals(ownership,100))
                throw new IllegalStateException("Fixture verification failed: owner references or shares");
            // Assert TEST row is invisible in the normal REAL context inside this transaction.
            context("REAL",null);
            Long leaked=db.queryForObject("select count(*) from construction_case where id=?",Long.class,caseId);
            if(!Objects.equals(leaked,0L))
                throw new IllegalStateException("RLS FAILURE: TEST fixture visible in REAL context; rollback");
            context("TEST",workspaceId);
            db.update("""
                insert into simulation_core_fixture
                 (id,workspace_id,request_key,case_id,property_id,owner_a_id,owner_b_id,created_by)
                values (?,?,?,?,?,?,?,?)
                """,fixtureId,workspaceId,key,caseId,propertyId,ownerA,ownerB,actor);
            return db.queryForMap("select * from simulation_core_fixture where id=?",fixtureId);
        }));
    }

    public List<Map<String,Object>> list(UUID workspaceId) {
        ensureReady();
        return Objects.requireNonNull(tx.execute(status -> {
            context("TEST",workspaceId);
            return db.queryForList("""
                select f.id,f.created_at,f.case_id,f.property_id,f.owner_a_id,f.owner_b_id,
                       c.project_code,c.title,c.status,
                       (select count(*) from property_owner where property_id=f.property_id) as owner_count
                from simulation_core_fixture f
                join construction_case c on c.id=f.case_id
                where f.workspace_id=? order by f.created_at desc limit 100
                """,workspaceId);
        }));
    }
}
