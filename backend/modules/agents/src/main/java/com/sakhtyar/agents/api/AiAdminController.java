package com.sakhtyar.agents.api;
import com.sakhtyar.agents.core.AgentRegistry;
import com.sakhtyar.agents.provider.AiModelRegistry;
import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/ai")
public class AiAdminController{
 private final JdbcTemplate jdbc;private final AgentRegistry agents;private final AiModelRegistry runtime;
 public AiAdminController(JdbcTemplate j,AgentRegistry a,AiModelRegistry r){jdbc=j;agents=a;runtime=r;}
 @GetMapping("/overview") public Map<String,Object> overview(){return Map.of("providers",count("ai_provider"),"models",count("ai_model"),"routes",count("ai_agent_route"),"prompts",count("ai_prompt"),"usage24h",jdbc.queryForObject("select count(*) from ai_usage_event where observed_at>=now()-interval '24 hours'",Long.class),"runtime",runtime.status());}
 @GetMapping("/agents") public Object agents(){return agents.descriptors();}
 @GetMapping("/providers") public List<Map<String,Object>> providers(){return jdbc.queryForList("select id,code,display_name,provider_type,base_url,enabled,lifecycle_status,priority,request_timeout_seconds,credential_configured,credential_last4,environment,health_status,last_health_check_at,last_health_message from ai_provider order by priority,display_name");}
 @PostMapping("/providers") public Map<String,Object> addProvider(@Valid @RequestBody ProviderRequest r){UUID id=UUID.randomUUID();jdbc.update("insert into ai_provider(id,code,display_name,provider_type,base_url,enabled,lifecycle_status,priority,request_timeout_seconds,environment,health_status,updated_at) values(?,?,?,?,?,?,?,?,?,?,'UNKNOWN',now())",id,code(r.code()),r.displayName().trim(),r.providerType().trim().toUpperCase(),clean(r.baseUrl()),r.enabled(),providerStatus(r.lifecycleStatus()),r.priority(),r.requestTimeoutSeconds(),def(r.environment(),"LOCAL"));return Map.of("id",id);}
 @PostMapping("/providers/{id}/test") public Map<String,Object> test(@PathVariable UUID id){String c=jdbc.queryForObject("select code from ai_provider where id=?",String.class,id);var p=runtime.provider(c);boolean ok=p.map(x->{try{return x.available();}catch(Exception e){return false;}}).orElse(false);jdbc.update("update ai_provider set health_status=?,last_health_check_at=now(),last_health_message=?,updated_at=now() where id=?",ok?"HEALTHY":"UNAVAILABLE",ok?"Runtime provider responded successfully.":"Runtime adapter unavailable or health check failed.",id);return Map.of("available",ok,"runtimeAdapter",p.isPresent());}
 @GetMapping("/models") public List<Map<String,Object>> models(){return jdbc.queryForList("select m.id,m.provider_id,p.code provider_code,m.code,m.display_name,m.provider_model_id,m.enabled,m.context_window,m.max_output_tokens,m.default_temperature,m.supports_json,m.supports_tools,m.supports_vision,m.supports_embeddings from ai_model m join ai_provider p on p.id=m.provider_id order by p.priority,m.display_name");}
 @PostMapping("/models") public Map<String,Object> addModel(@Valid @RequestBody ModelRequest r){UUID id=UUID.randomUUID();jdbc.update("insert into ai_model(id,provider_id,code,display_name,provider_model_id,enabled,context_window,max_output_tokens,default_temperature,supports_json,supports_tools,supports_vision,supports_embeddings,input_cost_per_million,output_cost_per_million) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,r.providerId(),code(r.code()),r.displayName().trim(),r.providerModelId().trim(),r.enabled(),r.contextWindow(),r.maxOutputTokens(),r.defaultTemperature(),r.supportsJson(),r.supportsTools(),r.supportsVision(),r.supportsEmbeddings(),r.inputCostPerMillion(),r.outputCostPerMillion());return Map.of("id",id);}
 @GetMapping("/routes") public List<Map<String,Object>> routes(){return jdbc.queryForList("select r.*,pp.code primary_provider_code,fp.code fallback_provider_code,cp.code canary_provider_code from ai_agent_route r left join ai_provider pp on pp.id=r.primary_provider_id left join ai_provider fp on fp.id=r.fallback_provider_id left join ai_provider cp on cp.id=r.canary_provider_id order by r.agent_code");}
 @PostMapping("/routes") public Map<String,Object> route(@Valid @RequestBody RouteRequest r){UUID id=jdbc.query("select id from ai_agent_route where agent_code=?",rs->rs.next()?rs.getObject(1,UUID.class):null,code(r.agentCode()));if(id==null)id=UUID.randomUUID();jdbc.update("insert into ai_agent_route(id,agent_code,primary_provider_id,primary_model_id,fallback_provider_id,fallback_model_id,canary_provider_id,canary_model_id,canary_weight_percent,routing_strategy,timeout_seconds,max_retries,require_json,require_tools,require_vision,local_only,max_cost_per_request,max_latency_ms,enabled,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now()) on conflict(agent_code) do update set primary_provider_id=excluded.primary_provider_id,primary_model_id=excluded.primary_model_id,fallback_provider_id=excluded.fallback_provider_id,fallback_model_id=excluded.fallback_model_id,canary_provider_id=excluded.canary_provider_id,canary_model_id=excluded.canary_model_id,canary_weight_percent=excluded.canary_weight_percent,routing_strategy=excluded.routing_strategy,timeout_seconds=excluded.timeout_seconds,max_retries=excluded.max_retries,require_json=excluded.require_json,require_tools=excluded.require_tools,require_vision=excluded.require_vision,local_only=excluded.local_only,max_cost_per_request=excluded.max_cost_per_request,max_latency_ms=excluded.max_latency_ms,enabled=excluded.enabled,updated_at=now()",id,code(r.agentCode()),r.primaryProviderId(),r.primaryModelId(),r.fallbackProviderId(),r.fallbackModelId(),r.canaryProviderId(),r.canaryModelId(),Math.max(0,Math.min(100,r.canaryWeightPercent())),def(r.routingStrategy(),"PRIMARY_FALLBACK"),r.timeoutSeconds(),r.maxRetries(),r.requireJson(),r.requireTools(),r.requireVision(),r.localOnly(),r.maxCostPerRequest(),r.maxLatencyMs(),r.enabled());return Map.of("id",id);}
 @GetMapping("/prompts") public List<Map<String,Object>> prompts(){return jdbc.queryForList("select p.id,p.code,p.agent_code,p.display_name,p.description,v.version_no,v.lifecycle_status,v.change_note,v.created_by,v.created_at from ai_prompt p left join lateral(select * from ai_prompt_version x where x.prompt_id=p.id order by x.version_no desc limit 1)v on true order by p.display_name");}
 @PostMapping("/prompts") public Map<String,Object> prompt(@Valid @RequestBody PromptRequest r){UUID pid=jdbc.query("select id from ai_prompt where code=?",rs->rs.next()?rs.getObject(1,UUID.class):null,code(r.code()));if(pid==null){pid=UUID.randomUUID();jdbc.update("insert into ai_prompt(id,code,agent_code,display_name,description) values(?,?,?,?,?)",pid,code(r.code()),clean(r.agentCode()),r.displayName().trim(),clean(r.description()));}Integer v=jdbc.queryForObject("select coalesce(max(version_no),0)+1 from ai_prompt_version where prompt_id=?",Integer.class,pid);UUID vid=UUID.randomUUID();jdbc.update("insert into ai_prompt_version(id,prompt_id,version_no,lifecycle_status,system_prompt,change_note,created_by) values(?,?,?,?,?,?,?)",vid,pid,v,promptStatus(r.lifecycleStatus()),r.systemPrompt(),clean(r.changeNote()),def(r.createdBy(),"admin"));return Map.of("id",pid,"versionId",vid,"version",v);}
 @GetMapping("/metrics") public Map<String,Object> metrics(){
  var out=new LinkedHashMap<String,Object>();
  out.put("summary",jdbc.queryForMap("""
   select count(*) requests,
          coalesce(sum(case when success then 1 else 0 end),0) successes,
          coalesce(sum(case when not success then 1 else 0 end),0) failures,
          coalesce(sum(case when fallback_used then 1 else 0 end),0) fallbacks,
          coalesce(round(avg(latency_ms)),0) avg_latency_ms,
          coalesce(percentile_cont(0.95) within group(order by latency_ms),0) p95_latency_ms,
          coalesce(sum(input_tokens),0) input_tokens,
          coalesce(sum(output_tokens),0) output_tokens,
          coalesce(sum(estimated_cost),0) estimated_cost
     from ai_usage_event where observed_at>=now()-interval '24 hours'
  """));
  out.put("providerAnalytics",jdbc.queryForList("""
   select provider_code,model_code,count(*) requests,
          coalesce(sum(case when success then 1 else 0 end),0) successes,
          coalesce(sum(case when fallback_used then 1 else 0 end),0) fallbacks,
          coalesce(round(avg(latency_ms)),0) avg_latency_ms,
          coalesce(percentile_cont(0.95) within group(order by latency_ms),0) p95_latency_ms,
          coalesce(sum(input_tokens+output_tokens),0) total_tokens,
          coalesce(sum(estimated_cost),0) estimated_cost
     from ai_usage_event where observed_at>=now()-interval '24 hours'
    group by provider_code,model_code order by requests desc
  """));
  out.put("agentAnalytics",jdbc.queryForList("""
   select agent_code,count(*) requests,
          coalesce(sum(case when success then 1 else 0 end),0) successes,
          coalesce(sum(case when fallback_used then 1 else 0 end),0) fallbacks,
          coalesce(round(avg(latency_ms)),0) avg_latency_ms,
          coalesce(percentile_cont(0.95) within group(order by latency_ms),0) p95_latency_ms
     from ai_usage_event where observed_at>=now()-interval '24 hours'
    group by agent_code order by requests desc
  """));
  out.put("trend",jdbc.queryForList("""
   select date_trunc('hour',observed_at) bucket,count(*) requests,
          coalesce(sum(case when not success then 1 else 0 end),0) failures,
          coalesce(sum(case when fallback_used then 1 else 0 end),0) fallbacks,
          coalesce(round(avg(latency_ms)),0) avg_latency_ms,
          coalesce(sum(input_tokens+output_tokens),0) tokens
     from ai_usage_event where observed_at>=now()-interval '24 hours'
    group by date_trunc('hour',observed_at) order by bucket
  """));
  out.put("qualityAvailable",false);
  out.put("qualityMessage","شاخص‌های کیفیت معنایی و correction rate هنوز به Evaluation Event واقعی متصل نشده‌اند؛ این صفحه فقط Reliability/Latency/Usage واقعی را نمایش می‌دهد.");
  return out;
 }
 @GetMapping("/usage") public List<Map<String,Object>> usage(){return jdbc.queryForList("select agent_code,provider_code,model_code,count(*) requests,sum(case when success then 1 else 0 end) successes,sum(case when fallback_used then 1 else 0 end) fallbacks,round(avg(latency_ms)) avg_latency_ms,coalesce(sum(input_tokens),0) input_tokens,coalesce(sum(output_tokens),0) output_tokens from ai_usage_event where observed_at>=now()-interval '24 hours' group by agent_code,provider_code,model_code order by requests desc");}
 @GetMapping("/budgets") public List<Map<String,Object>> budgets(){return jdbc.queryForList("select * from ai_budget_policy order by scope_type,scope_code");}
 @GetMapping("/guardrails") public List<Map<String,Object>> guardrails(){return jdbc.queryForList("select * from ai_guardrail_policy order by display_name");}
 private long count(String t){Long n=jdbc.queryForObject("select count(*) from "+t,Long.class);return n==null?0:n;}private static String clean(String s){return s==null||s.isBlank()?null:s.trim();}private static String code(String s){return s==null?"":s.trim().toLowerCase().replace(' ','-');}private static String def(String s,String d){String v=clean(s);return v==null?d:v;}
 private static String providerStatus(String s){String v=def(s,"DRAFT").toUpperCase();if(!List.of("DRAFT","TESTED","ACTIVE","DISABLED","FAILED").contains(v))throw new IllegalArgumentException("Invalid provider status");return v;}private static String promptStatus(String s){String v=def(s,"DRAFT").toUpperCase();if(!List.of("DRAFT","STAGING","PRODUCTION","ARCHIVED").contains(v))throw new IllegalArgumentException("Invalid prompt status");return v;}
 public record ProviderRequest(@NotBlank String code,@NotBlank String displayName,@NotBlank String providerType,String baseUrl,boolean enabled,String lifecycleStatus,int priority,int requestTimeoutSeconds,String environment){}
 public record ModelRequest(UUID providerId,@NotBlank String code,@NotBlank String displayName,@NotBlank String providerModelId,boolean enabled,Integer contextWindow,Integer maxOutputTokens,BigDecimal defaultTemperature,boolean supportsJson,boolean supportsTools,boolean supportsVision,boolean supportsEmbeddings,BigDecimal inputCostPerMillion,BigDecimal outputCostPerMillion){}
 public record RouteRequest(@NotBlank String agentCode,UUID primaryProviderId,UUID primaryModelId,UUID fallbackProviderId,UUID fallbackModelId,UUID canaryProviderId,UUID canaryModelId,int canaryWeightPercent,String routingStrategy,int timeoutSeconds,int maxRetries,boolean requireJson,boolean requireTools,boolean requireVision,boolean localOnly,BigDecimal maxCostPerRequest,Long maxLatencyMs,boolean enabled){}
 public record PromptRequest(@NotBlank String code,String agentCode,@NotBlank String displayName,String description,@NotBlank String systemPrompt,String lifecycleStatus,String changeNote,String createdBy){}
}