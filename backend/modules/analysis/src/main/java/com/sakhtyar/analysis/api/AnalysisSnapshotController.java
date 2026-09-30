package com.sakhtyar.analysis.api;

import static com.sakhtyar.analysis.api.AnalysisSnapshotDtos.*;
import com.sakhtyar.analysis.application.AnalysisSnapshotService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analysis-snapshots")
public class AnalysisSnapshotController {
    private final AnalysisSnapshotService service;
    public AnalysisSnapshotController(AnalysisSnapshotService service){this.service=service;}

    @PostMapping
    public SnapshotResponse create(@Valid @RequestBody CreateSnapshotRequest request,Authentication auth) {
        return service.create(request,auth);
    }

    @GetMapping("/{id}")
    public SnapshotResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/cases/{caseId}")
    public List<SnapshotResponse> byCase(@PathVariable UUID caseId,
            @RequestParam(required=false) String analysisType) {
        return service.byCase(caseId,analysisType);
    }

    @GetMapping("/scenarios/{scenarioId}")
    public List<SnapshotResponse> byScenario(@PathVariable UUID scenarioId) {
        return service.byScenario(scenarioId);
    }

    @GetMapping("/{id}/lineage")
    public List<LineageResponse> lineage(@PathVariable UUID id) {
        return service.lineage(id);
    }
}