package com.sakhtyar.operations;

import com.sakhtyar.agents.provider.AiModelRegistry;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OperationsService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final AiModelRegistry ai;
    private final ObjectMapper mapper;
    private final RestClient rest=RestClient.create();
    private final String prometheus,loki,tempo,grafana,minio;

    public OperationsService(JdbcTemplate jdbc,StringRedisTemplate redis,AiModelRegistry ai,ObjectMapper mapper,
      @Value("${app.operations.prometheus-url:http://localhost:9090}") String prometheus,
      @Value("${app.operations.loki-url:http://localhost:3100}") String loki,
      @Value("${app.operations.tempo-url:http://localhost:3200}") String tempo,
      @Value("${app.operations.grafana-url:http://localhost:13001}") String grafana,
      @Value("${app.storage.endpoint:http://localhost:9000}") String minio){
        this.jdbc=jdbc;this.redis=redis;this.ai=ai;this.mapper=mapper;
        this.prometheus=trim(prometheus);this.loki=trim(loki);this.tempo=trim(tempo);this.grafana=trim(grafana);this.minio=trim(minio);
    }

    public Map<String,Object> summary(){
        List<Map<String,Object>> s=services();
        long coreDown=s.stream().filter(x->"CORE".equals(x.get("group"))).filter(x->!"HEALTHY".equals(x.get("status"))).count();
        long aiDown=s.stream().filter(x->"AI".equals(x.get("group"))).filter(x->!"HEALTHY".equals(x.get("status"))).count();
        long obsDown=s.stream().filter(x->"OBSERVABILITY".equals(x.get("group"))).filter(x->!"HEALTHY".equals(x.get("status"))).count();
        LinkedHashMap<String,Object> r=new LinkedHashMap<>();
        r.put("status",coreDown>0?"UNHEALTHY":aiDown>0?"DEGRADED":"HEALTHY");
        r.put("coreStatus",coreDown==0?"HEALTHY":"UNHEALTHY");
        r.put("aiStatus",aiDown==0?"HEALTHY":"DEGRADED");
        r.put("observabilityStatus",obsDown==0?"HEALTHY":"DEGRADED");
        r.put("checkedAt",Instant.now());
        r.put("uptimeSeconds",ManagementFactory.getRuntimeMXBean().getUptime()/1000L);
        r.put("services",s);r.put("performance",performance());
        r.put("activeIncidents",incidents().stream().filter(i->!"INFO".equals(i.get("severity"))).count());
        r.put("grafanaUrl",grafana);return r;
    }

    public List<Map<String,Object>> services(){
        ArrayList<Map<String,Object>> r=new ArrayList<>();
        r.add(service("API","CORE","HEALTHY","Application process is running.",0L));
        long st=System.nanoTime();
        try{jdbc.queryForObject("select 1",Integer.class);r.add(service("PostgreSQL","CORE","HEALTHY","Database query succeeded.",elapsed(st)));}
        catch(Exception e){r.add(service("PostgreSQL","CORE","UNAVAILABLE",safe(e),elapsed(st)));}
        st=System.nanoTime();
        try{redis.opsForValue().get("__sakhtyar_health_probe__");r.add(service("Redis","CORE","HEALTHY","Redis connection succeeded.",elapsed(st)));}
        catch(Exception e){r.add(service("Redis","CORE","UNAVAILABLE",safe(e),elapsed(st)));}
        r.add(http("MinIO","CORE",minio+"/minio/health/live"));
        var a=ai.status();r.add(service("AI Provider","AI",a.available()?"HEALTHY":"UNAVAILABLE",a.message(),null));
        r.add(http("Prometheus","OBSERVABILITY",prometheus+"/-/ready"));
        r.add(http("Loki","OBSERVABILITY",loki+"/ready"));
        r.add(http("Tempo","OBSERVABILITY",tempo+"/ready"));
        r.add(grafana());return r;
    }

    public Map<String,Object> performance(){
        LinkedHashMap<String,Object> r=new LinkedHashMap<>();
        r.put("requestRate",prom("sum(rate(http_server_requests_seconds_count[5m]))"));
        r.put("errorRate",prom("100 * sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count[5m])),0.001)"));
        r.put("p95LatencySeconds",prom("histogram_quantile(0.95,sum(rate(http_server_requests_seconds_bucket[5m])) by (le))"));
        r.put("jvmHeapBytes",prom("sum(jvm_memory_used_bytes{area=\"heap\"})"));
        r.put("processCpuUsage",prom("process_cpu_usage"));
        r.put("dbActiveConnections",prom("sum(hikaricp_connections_active)"));
        r.put("dbPendingConnections",prom("sum(hikaricp_connections_pending)"));
        return r;
    }

    public Map<String,Object> trends(String range){
        Window w=window(range);LinkedHashMap<String,Object> r=new LinkedHashMap<>();
        r.put("requestRate",range("sum(rate(http_server_requests_seconds_count[5m]))",w));
        r.put("errorRate",range("100 * sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count[5m])),0.001)",w));
        r.put("p95LatencySeconds",range("histogram_quantile(0.95,sum(rate(http_server_requests_seconds_bucket[5m])) by (le))",w));
        r.put("cpuUsage",range("process_cpu_usage",w));r.put("heapBytes",range("sum(jvm_memory_used_bytes{area=\"heap\"})",w));return r;
    }

    public List<Map<String,Object>> slowEndpoints(){
        return vector("topk(8,histogram_quantile(0.95,sum(rate(http_server_requests_seconds_bucket[5m])) by (le,uri,method)))");
    }

    public Map<String,Object> infrastructure(){
        return new LinkedHashMap<>(Map.of(
          "jvmHeapBytes",nullable(prom("sum(jvm_memory_used_bytes{area=\"heap\"})")),
          "jvmMaxHeapBytes",nullable(prom("sum(jvm_memory_max_bytes{area=\"heap\"})")),
          "threadsLive",nullable(prom("jvm_threads_live_threads")),
          "gcPauseRate",nullable(prom("sum(rate(jvm_gc_pause_seconds_count[5m]))")),
          "processCpuUsage",nullable(prom("process_cpu_usage")),
          "dbActiveConnections",nullable(prom("sum(hikaricp_connections_active)")),
          "dbIdleConnections",nullable(prom("sum(hikaricp_connections_idle)")),
          "dbPendingConnections",nullable(prom("sum(hikaricp_connections_pending)"))
        ));
    }

    public List<Map<String,Object>> dependencies(){
        return vector("sum(rate(traces_service_graph_request_total[5m])) by (client,server)");
    }

    public List<Map<String,Object>> incidents(){
        ArrayList<Map<String,Object>> r=new ArrayList<>();
        for(Map<String,Object>s:services()){
            if("HEALTHY".equals(s.get("status")))continue;
            String group=String.valueOf(s.get("group"));
            String severity="CORE".equals(group)?"CRITICAL":"WARNING";
            String impact=switch(group){
                case "CORE"->"ممکن است بخشی از عملکرد اصلی سامانه برای کاربران مختل باشد.";
                case "AI"->"قابلیت‌های هوش مصنوعی ممکن است کند یا موقتاً غیرفعال باشند؛ هسته سامانه مستقل است.";
                default->"ابزار پایش یا تحلیل عمیق در دسترس نیست؛ ترافیک اصلی سامانه لزوماً تحت تأثیر نیست.";
            };
            r.add(Map.of("source","HEALTH","severity",severity,"service",String.valueOf(s.get("name")),"title",s.get("name")+" is "+s.get("status"),"summary",String.valueOf(s.get("message")),"impact",impact,"group",group));
        }
        if(r.isEmpty())r.add(Map.of("source","SYSTEM","severity","INFO","service","SakhtYar","title","رخداد فعال مهمی مشاهده نشد","summary","همه سرویس‌های مانیتورشده پاسخ سالم داده‌اند.","impact","نیازی به اقدام فوری نیست.","group","CORE"));
        return r;
    }

    public Map<String,Object> correlation(String id){
        if(id==null||!id.matches("[A-Za-z0-9._:-]{1,128}"))throw new IllegalArgumentException("Invalid correlation id.");
        List<Map<String,Object>> logs=lokiLogs(id);ArrayList<String> traces=new ArrayList<>();
        for(Map<String,Object>l:logs){Object t=l.get("traceId");if(t!=null&&!String.valueOf(t).isBlank()&&!traces.contains(String.valueOf(t)))traces.add(String.valueOf(t));}
        LinkedHashMap<String,Object> r=new LinkedHashMap<>();r.put("correlationId",id);r.put("logsAvailable",check(loki+"/ready"));r.put("tracesAvailable",check(tempo+"/ready"));r.put("logs",logs);r.put("traceIds",traces);return r;
    }

    public Map<String,String> links(){return Map.of("grafana",grafana,"prometheus",prometheus);}

    private Map<String,Object> grafana(){
        long st=System.nanoTime();
        try{String b=rest.get().uri(grafana+"/api/health").retrieve().body(String.class);boolean ok=b!=null&&b.contains("\"database\"")&&b.toLowerCase().contains("ok");return service("Grafana","OBSERVABILITY",ok?"HEALTHY":"DEGRADED",ok?"Grafana health API responded successfully.":"Grafana responded but health payload was not recognized.",elapsed(st));}
        catch(Exception e){return service("Grafana","OBSERVABILITY","UNAVAILABLE",safe(e),elapsed(st));}
    }
    private Map<String,Object> http(String n,String g,String u){long st=System.nanoTime();try{rest.get().uri(u).retrieve().toBodilessEntity();return service(n,g,"HEALTHY","Health endpoint responded.",elapsed(st));}catch(Exception e){return service(n,g,"UNAVAILABLE",safe(e),elapsed(st));}}
    private Map<String,Object> service(String n,String g,String s,String m,Long l){LinkedHashMap<String,Object>r=new LinkedHashMap<>();r.put("name",n);r.put("group",g);r.put("status",s);r.put("message",m);r.put("latencyMs",l);r.put("checkedAt",Instant.now());return r;}
    private Double prom(String q){try{JsonNode a=json(prometheus+"/api/v1/query",q);JsonNode rs=a.path("data").path("result");if(!rs.isArray()||rs.isEmpty())return null;JsonNode v=rs.get(0).path("value");return v.isArray()&&v.size()>=2?num(v.get(1).asText()):null;}catch(Exception e){return null;}}
    private List<Map<String,Object>> range(String q,Window w){try{URI u=UriComponentsBuilder.fromUriString(prometheus+"/api/v1/query_range").queryParam("query",q).queryParam("start",w.start().getEpochSecond()).queryParam("end",w.end().getEpochSecond()).queryParam("step",w.step()).build().encode().toUri();JsonNode rs=read(u).path("data").path("result");if(!rs.isArray()||rs.isEmpty())return List.of();ArrayList<Map<String,Object>>x=new ArrayList<>();for(JsonNode v:rs.get(0).path("values")){Double n=num(v.get(1).asText());if(n!=null)x.add(Map.of("timestamp",v.get(0).asDouble(),"value",n));}return x;}catch(Exception e){return List.of();}}
    private List<Map<String,Object>> vector(String q){try{JsonNode rs=json(prometheus+"/api/v1/query",q).path("data").path("result");ArrayList<Map<String,Object>>x=new ArrayList<>();if(!rs.isArray())return x;for(JsonNode i:rs){LinkedHashMap<String,Object>m=new LinkedHashMap<>();i.path("metric").properties().forEach(e->m.put(e.getKey(),e.getValue().asText()));JsonNode v=i.path("value");if(v.isArray()&&v.size()>=2)m.put("value",num(v.get(1).asText()));x.add(m);}return x;}catch(Exception e){return List.of();}}
    private List<Map<String,Object>> lokiLogs(String id){try{String q="{container=~\"sakhtyar.*\"} |= \""+id+"\"";URI u=UriComponentsBuilder.fromUriString(loki+"/loki/api/v1/query_range").queryParam("query",q).queryParam("limit",30).queryParam("direction","BACKWARD").build().encode().toUri();JsonNode rs=read(u).path("data").path("result");ArrayList<Map<String,Object>>x=new ArrayList<>();for(JsonNode st:rs){for(JsonNode v:st.path("values")){String raw=v.get(1).asText();LinkedHashMap<String,Object>m=new LinkedHashMap<>();try{JsonNode l=mapper.readTree(raw);m.put("level",l.path("log").path("level").asText(""));m.put("message",l.path("message").asText(raw));m.put("traceId",l.path("traceId").asText(""));}catch(Exception ignored){m.put("level","");m.put("message",raw);m.put("traceId","");}x.add(m);}}return x;}catch(Exception e){return List.of();}}
    private JsonNode json(String path,String q)throws Exception{URI u=UriComponentsBuilder.fromUriString(path).queryParam("query",q).build().encode().toUri();return read(u);}
    private JsonNode read(URI u)throws Exception{String b=rest.get().uri(u).retrieve().body(String.class);return b==null?mapper.createObjectNode():mapper.readTree(b);}
    private boolean check(String u){try{rest.get().uri(u).retrieve().toBodilessEntity();return true;}catch(Exception e){return false;}}
    private Window window(String v){Instant e=Instant.now();return switch(v==null?"6h":v){case"1h"->new Window(e.minus(1,ChronoUnit.HOURS),e,30);case"24h"->new Window(e.minus(24,ChronoUnit.HOURS),e,300);case"7d"->new Window(e.minus(7,ChronoUnit.DAYS),e,1800);default->new Window(e.minus(6,ChronoUnit.HOURS),e,120);};}
    private static long elapsed(long s){return(System.nanoTime()-s)/1_000_000L;}private static String trim(String s){return s==null?"":s.replaceAll("/+$","");}private static String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private static Double num(String s){try{double d=Double.parseDouble(s);return Double.isFinite(d)?d:null;}catch(Exception e){return null;}}private static Object nullable(Object v){return v==null?Double.NaN:v;}
    private record Window(Instant start,Instant end,int step){}
}