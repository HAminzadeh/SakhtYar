package com.sakhtyar.operations;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/operations")
public class OperationsController{
 private final OperationsService s;public OperationsController(OperationsService s){this.s=s;}
 @GetMapping("/summary") public Map<String,Object> summary(){return s.summary();}
 @GetMapping("/services") public List<Map<String,Object>> services(){return s.services();}
 @GetMapping("/performance") public Map<String,Object> performance(){return s.performance();}
 @GetMapping("/trends") public Map<String,Object> trends(@RequestParam(defaultValue="6h")String range){return s.trends(range);}
 @GetMapping("/slow-endpoints") public List<Map<String,Object>> slow(){return s.slowEndpoints();}
 @GetMapping("/infrastructure") public Map<String,Object> infrastructure(){return s.infrastructure();}
 @GetMapping("/dependencies") public List<Map<String,Object>> dependencies(){return s.dependencies();}
 @GetMapping("/incidents") public List<Map<String,Object>> incidents(){return s.incidents();}
 @GetMapping("/correlation/{id}") public Map<String,Object> correlation(@PathVariable String id){return s.correlation(id);}
 @GetMapping("/links") public Map<String,String> links(){return s.links();}
}