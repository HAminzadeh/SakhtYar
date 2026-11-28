package com.sakhtyar.assembly.api;

import com.sakhtyar.assembly.application.AssemblyDefinitionService;
import com.sakhtyar.assembly.application.AssemblyDefinitionService.Definition;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Read-only revision API; existing assembly RBAC also protects these routes. */
@RestController
@RequestMapping("/api/v1/assemblies")
public class AssemblyDefinitionController {
    private final AssemblyDefinitionService service;
    public AssemblyDefinitionController(AssemblyDefinitionService service) { this.service = service; }

    @GetMapping("/{id}/definition")
    public Definition current(@PathVariable UUID id) { return service.current(id); }

    @GetMapping("/{id}/revisions")
    public List<Definition> revisions(@PathVariable UUID id,
            @RequestParam(defaultValue="20") int limit,
            @RequestParam(required=false) Long beforeRevision) {
        return service.revisions(id, limit, beforeRevision);
    }

    @GetMapping("/{id}/revisions/{revision}")
    public Definition revision(@PathVariable UUID id, @PathVariable long revision) {
        return service.revision(id, revision);
    }

    @GetMapping("/estimates/{id}/definition")
    public Definition estimateDefinition(@PathVariable UUID id) { return service.estimateDefinition(id); }
}
