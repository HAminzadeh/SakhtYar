package com.sakhtyar.monitoring.api;

import static com.sakhtyar.monitoring.api.MonitoringDtos.*;
import com.sakhtyar.monitoring.application.MonitoringService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {
  private final MonitoringService service;
  public MonitoringController(MonitoringService service){this.service=service;}
  @PostMapping("/rules") public RuleResponse create(@Valid @RequestBody UpsertRuleRequest r){return service.create(r);}
  @GetMapping("/rules") public List<RuleResponse> rules(){return service.list();}
  @PostMapping("/evaluate") public List<EventResponse> evaluate(){return service.evaluateAll();}
  @GetMapping("/rules/{id}/events") public List<EventResponse> events(@PathVariable UUID id,@RequestParam(defaultValue="100")int limit){return service.events(id,limit);}
}
