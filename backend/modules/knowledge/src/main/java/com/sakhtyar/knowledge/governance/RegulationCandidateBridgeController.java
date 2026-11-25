package com.sakhtyar.knowledge.governance;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only read-only preview; protected also by the service and SecurityConfig. */
@RestController
@RequestMapping("/api/v1/knowledge/admin/phase17/bridge")
public class RegulationCandidateBridgeController {
    private final RegulationCandidateBridgeService service;
    public RegulationCandidateBridgeController(RegulationCandidateBridgeService service) {
        this.service = service;
    }
    @GetMapping("/candidates")
    public Map<String,Object> candidates(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "ALL") String recordType) {
        return service.candidates(page,pageSize,recordType);
    }
}
