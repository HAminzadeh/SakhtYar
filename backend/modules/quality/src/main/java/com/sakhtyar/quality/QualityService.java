package com.sakhtyar.quality;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Service
public class QualityService implements ApplicationListener<ApplicationReadyEvent> {
    public record RunRequest(List<String> ids, String module, String requestKey) {}
    private final JdbcTemplate jdbc;
    private final QualityCatalog catalog;
    private final RequestMappingHandlerMapping routes;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"sakhtyar-quality-worker"); t.setDaemon(true); return t;
    });
    private final String commit;
    private final String environment;
    private final Object mutex = new Object();
    private boolean busy = false;

    public QualityService(JdbcTemplate jdbc, QualityCatalog catalog,
                          @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping routes,
                          @Value("${app.quality.git-commit:unknown}") String commit,
                          @Value("${app.quality.environment:local}") String environment) {
        this.jdbc=jdbc;this.catalog=catalog;this.routes=routes;
        this.commit=commit;this.environment=environment;
    }

    @Override public void onApplicationEvent(ApplicationReadyEvent event) {
        // A crashed process cannot be silently marked green. All interrupted work is explicit.
        jdbc.update("UPDATE quality_test_result SET status='BLOCKED', actual='Backend restarted during execution', finished_at=now() WHERE status='QUEUED' AND run_id IN (SELECT id FROM quality_test_run WHERE status IN ('QUEUED','RUNNING'))");
        jdbc.update("""
          UPDATE quality_test_run SET status='INTERRUPTED',finished_at=now(),
             passed=(SELECT count(*) FROM quality_test_result x WHERE x.run_id=quality_test_run.id AND x.status='PASSED'),
             failed=(SELECT count(*) FROM quality_test_result x WHERE x.run_id=quality_test_run.id AND x.status='FAILED'),
             blocked=(SELECT count(*) FROM quality_test_result x WHERE x.run_id=quality_test_run.id AND x.status='BLOCKED')
          WHERE status IN ('QUEUED','RUNNING')
        """);
    }
    @jakarta.annotation.PreDestroy public void shutdown(){worker.shutdownNow();}

    public List<QualityCatalog.TestDefinition> catalog(){return catalog.all();}
    public Map<String,Object> overview(){
        long total=catalog.all().size();
        long runnable=catalog.all().stream().filter(d->!d.kind().equals("EXTERNAL")).count();
        List<Map<String,Object>> history=history(20);
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("totalScenarios",total);r.put("runnableSafeScenarios",runnable);
        r.put("requiresIsolatedRunner",total-runnable);
        r.put("totalRuns",jdbc.queryForObject("SELECT count(*) FROM quality_test_run",Long.class));
        r.put("latestRun",history.isEmpty()?null:history.get(0));
        r.put("recentRuns",history);return r;
    }
    public List<Map<String,Object>> history(int limit){
        int safe=Math.min(100,Math.max(1,limit));
        return jdbc.queryForList("SELECT * FROM quality_test_run ORDER BY started_at DESC LIMIT ?",safe);
    }
    public List<Map<String,Object>> testHistory(String testId,int limit){
        catalog.require(testId);
        int safe=Math.min(100,Math.max(1,limit));
        return jdbc.queryForList("""
          SELECT r.id AS run_id, r.started_at, r.environment, r.git_commit, r.scope,
                 x.status, x.actual, x.detail, x.duration_ms
          FROM quality_test_result x JOIN quality_test_run r ON r.id=x.run_id
          WHERE x.test_id=? ORDER BY r.started_at DESC LIMIT ?
          """,testId,safe);
    }
    public Map<String,Object> run(UUID id){
        var rs=jdbc.queryForList("SELECT * FROM quality_test_run WHERE id=?",id);
        if(rs.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Run not found");
        var result=new LinkedHashMap<String,Object>(rs.get(0));
        result.put("results",jdbc.queryForList("SELECT * FROM quality_test_result WHERE run_id=? ORDER BY test_id",id));
        return result;
    }
    public Map<String,Object> create(RunRequest request,String username){
        if(request==null || request.requestKey()==null || !request.requestKey().matches("[A-Za-z0-9_-]{8,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid idempotency requestKey required");
        // Idempotency: a repeated request from the same user always returns its previous run.
        var previous=jdbc.queryForList("SELECT id FROM quality_test_run WHERE requested_by=? AND request_key=?",username,request.requestKey());
        if(!previous.isEmpty())return run((UUID)previous.get(0).get("id"));
        List<QualityCatalog.TestDefinition> selected;
        String scope;
        if(request.ids()!=null && !request.ids().isEmpty()){
            if(request.ids().size()>250)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Too many ids");
            var unique=new LinkedHashSet<>(request.ids());
            try{selected=unique.stream().map(catalog::require).toList();}
            catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
            scope="selected";
        }else if(request.module()!=null && !request.module().isBlank() && !request.module().equals("all")){
            String module=request.module().trim();
            selected=catalog.all().stream().filter(d->d.module().equals(module)).toList();
            scope="module:"+module;
        }else{selected=catalog.all();scope="all";}
        if(selected.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No scenarios selected");
        UUID id=UUID.randomUUID();
        synchronized(mutex){
            if(busy)throw new ResponseStatusException(HttpStatus.CONFLICT,"Quality runner busy");
            busy=true;
            try{
                jdbc.update("INSERT INTO quality_test_run(id,request_key,requested_by,scope,environment,git_commit,status,total) VALUES (?,?,?,?,?,?,'QUEUED',?)",
                        id,request.requestKey(),username,scope,environment,commit,selected.size());
                for(var d:selected){
                    jdbc.update("INSERT INTO quality_test_result(run_id,test_id,module,title,status,expected) VALUES (?,?,?,?,'QUEUED',?)",
                        id,d.id(),d.module(),d.title(),d.expected());
                }
            }catch(RuntimeException e){busy=false;throw e;}
            try{worker.execute(()->execute(id,selected));}
            catch(RejectedExecutionException e){busy=false;throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Runner unavailable");}
        }
        return run(id);
    }
    private void execute(UUID id,List<QualityCatalog.TestDefinition> selected){
        try{
            jdbc.update("UPDATE quality_test_run SET status='RUNNING' WHERE id=?",id);
            for(var d:selected){
                long start=System.nanoTime();
                Instant time=Instant.now();
                String status="BLOCKED",actual="",detail="";
                try{
                    if(d.kind().equals("EXTERNAL")){
                        actual="نیازمند Runner مستقل یا داده آزمایشی؛ از این UI اجرا نشده است.";
                        detail="Not executed: requires isolated test environment and a purpose-built assertion.";
                    }else if(d.kind().equals("ROUTE")){
                        boolean mapped=routeExists(d.target());
                        status=mapped?"PASSED":"FAILED";
                        actual=mapped?"Backend HTTP route is registered":"Backend HTTP route was not found";
                        detail="Smoke test only: route mapping exists; does NOT validate business logic, HTTP authorization or E2E behavior.";
                    }else if(d.kind().equals("SQL")){
                        boolean ok=sqlCheck(d.id());
                        status=ok?"PASSED":"FAILED";
                        actual=ok?"Read-only database assertion satisfied":"Read-only database assertion failed";
                        detail="SQL schema/read-only assertion; no domain workflow was executed.";
                    }
                }catch(Exception e){
                    status="FAILED";actual="Check raised "+e.getClass().getSimpleName();
                    detail="See backend correlation/logs. Sensitive exception messages are not included.";
                }
                long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
                try{jdbc.update("UPDATE quality_test_result SET status=?,actual=?,detail=?,duration_ms=?,started_at=?,finished_at=now() WHERE run_id=? AND test_id=?",
                    status,actual,detail,elapsed,java.sql.Timestamp.from(time),id,d.id());}
                catch(Exception persistFailure){
                    // The final run will be INTERRUPTED on next startup; never fabricate a PASS.
                    throw persistFailure;
                }
            }
            jdbc.update("""
              UPDATE quality_test_run SET status='COMPLETED',finished_at=now(),
                passed=(SELECT count(*) FROM quality_test_result x WHERE x.run_id=? AND x.status='PASSED'),
                failed=(SELECT count(*) FROM quality_test_result x WHERE x.run_id=? AND x.status='FAILED'),
                blocked=(SELECT count(*) FROM quality_test_result x WHERE x.run_id=? AND x.status='BLOCKED')
              WHERE id=?
            """,id,id,id,id);
        }catch(Exception e){
            try{
                jdbc.update("UPDATE quality_test_result SET status='BLOCKED',actual='Execution aborted' WHERE run_id=? AND status='QUEUED'",id);
                jdbc.update("UPDATE quality_test_run SET status='INTERRUPTED',finished_at=now() WHERE id=?",id);
            }catch(Exception ignored){/* restart reconciliation will preserve actual state */}
        }finally{synchronized(mutex){busy=false;}}
    }
    private boolean routeExists(String target){
        var chunks=target.split(" ",2);
        if(chunks.length!=2)return false;
        String method=chunks[0].toUpperCase(Locale.ROOT),path=chunks[1];
        return routes.getHandlerMethods().keySet().stream().anyMatch(info ->
            info.getPatternValues().contains(path) &&
            (info.getMethodsCondition().getMethods().isEmpty() || info.getMethodsCondition().getMethods().stream().anyMatch(x->x.name().equals(method)))
        );
    }
    // No user-supplied SQL is executed: every SQL probe is compiled into this immutable whitelist.
    private boolean sqlCheck(String id){
        return switch(id){
            case "INF-001" -> Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1",Integer.class));
            case "INF-002" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='55' AND success=true)",Boolean.class));
            case "INF-003" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT count(*) FILTER (WHERE success=false)=0 FROM flyway_schema_history",Boolean.class));
            case "INF-004" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.app_user') IS NOT NULL",Boolean.class));
            case "INF-005" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.auth_session') IS NOT NULL",Boolean.class));
            case "KNW-001" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.knowledge_ai_import_item') IS NOT NULL",Boolean.class));
            case "KNW-002" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.knowledge_ai_text_revision') IS NOT NULL",Boolean.class));
            case "QTC-001" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.quality_test_run') IS NOT NULL",Boolean.class));
            case "QTC-002" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.quality_test_result') IS NOT NULL",Boolean.class));
            default -> throw new IllegalArgumentException("No SQL probe allowlisted for " + id);
        };
    }
}
