package com.sakhtyar.webbff.api;
import com.sakhtyar.webbff.application.WebBffService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web/v1")
public class WebBffController {
  private final WebBffService service;
  public WebBffController(WebBffService service){this.service=service;}

  @GetMapping("/public/health")
  public Map<String,Object> health(){return service.health();}

  @GetMapping("/public/market")
  public Map<String,Object> market(
      @RequestParam String city,
      @RequestParam(required=false)String district,
      @RequestParam(required=false)String neighborhood,
      @RequestParam(required=false)String propertyType,
      @RequestParam(defaultValue="30")int days
  ){
    return service.publicMarket(city,district,neighborhood,propertyType,days);
  }

  @GetMapping("/cases/{caseId}/summary")
  public Map<String,Object> caseSummary(@PathVariable UUID caseId){
    return service.caseSummary(caseId);
  }
}
