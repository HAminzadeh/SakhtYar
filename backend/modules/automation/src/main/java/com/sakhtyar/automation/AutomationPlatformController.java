package com.sakhtyar.automation;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/automation")
public class AutomationPlatformController {
  private final AutomationPlatformService service;
  public AutomationPlatformController(AutomationPlatformService service){this.service=service;}
  @GetMapping("/status") public Map<String,Object> status() throws Exception{return service.status();}
  @PostMapping("/check") public Map<String,Object> check() throws Exception{return service.run(true);}
  @PostMapping("/repair") public Map<String,Object> repair() throws Exception{return service.run(false);}
}