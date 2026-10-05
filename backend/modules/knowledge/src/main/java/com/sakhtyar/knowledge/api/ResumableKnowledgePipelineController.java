package com.sakhtyar.knowledge.api;
import com.sakhtyar.knowledge.application.ResumableKnowledgePipelineService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/knowledge/admin/pipeline")
public class ResumableKnowledgePipelineController{
 private final ResumableKnowledgePipelineService s;public ResumableKnowledgePipelineController(ResumableKnowledgePipelineService s){this.s=s;}
 public record StartRequest(String sourceRoot){} public record SelectionRequest(List<String> relativePaths){}
 @PostMapping("/start") public Map<String,Object> start(@RequestBody(required=false) StartRequest r,Authentication a){return s.start(r==null?null:r.sourceRoot(),a==null?"system":a.getName());}
 @GetMapping("/recent") public List<Map<String,Object>> recent(){return s.recent();}
 @GetMapping("/{id}") public Map<String,Object> state(@PathVariable UUID id){return s.state(id);}
 @GetMapping("/{id}/events") public List<Map<String,Object>> events(@PathVariable UUID id,@RequestParam(defaultValue="0") long after){return s.events(id,after);}
 @PostMapping("/{id}/resume") public Map<String,Object> resume(@PathVariable UUID id){return s.resume(id);}
 @PostMapping("/{id}/stop") public Map<String,Object> stop(@PathVariable UUID id){return s.stop(id);}
 @PostMapping("/{id}/retry") public Map<String,Object> retry(@PathVariable UUID id){return s.retry(id);}
 @PostMapping("/{id}/selection") public Map<String,Object> select(@PathVariable UUID id,@RequestBody SelectionRequest r){return s.select(id,r.relativePaths());}
}