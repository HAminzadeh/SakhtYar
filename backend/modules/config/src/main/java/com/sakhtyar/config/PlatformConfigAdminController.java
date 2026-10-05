package com.sakhtyar.config;
import org.springframework.web.bind.annotation.*;import java.security.Principal;import java.util.*;
@RestController @RequestMapping("/api/v1/admin/config") public class PlatformConfigAdminController {
 private final PlatformConfigService s; public PlatformConfigAdminController(PlatformConfigService s){this.s=s;} private String actor(Principal p){return p==null?"system":p.getName();}
 @GetMapping public Map<String,Object> current(){return s.current();}
 @GetMapping("/versions/{id}/values") public List<Map<String,Object>> values(@PathVariable UUID id){return s.values(id);}
 @PostMapping("/versions") public Map<String,Object> draft(@RequestBody Map<String,String>b,Principal p){return Map.of("id",s.draft(b.getOrDefault("environment","local"),b.getOrDefault("reason","Admin draft"),actor(p)));}
 @PutMapping("/versions/{id}/values/{key}") public void put(@PathVariable UUID id,@PathVariable String key,@RequestBody Map<String,String>b,Principal p){s.put(id,key,b.get("value"),b.getOrDefault("scopeType","GLOBAL"),b.getOrDefault("scopeKey",""),actor(p));}
 @PostMapping("/versions/{id}/publish") public void publish(@PathVariable UUID id,Principal p){s.publish(id,actor(p));}
}