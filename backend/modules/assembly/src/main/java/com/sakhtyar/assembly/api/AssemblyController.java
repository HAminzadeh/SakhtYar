package com.sakhtyar.assembly.api;

import static com.sakhtyar.assembly.api.AssemblyDtos.*;

import com.sakhtyar.assembly.application.AssemblyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assemblies")
public class AssemblyController {

    private final AssemblyService service;

    public AssemblyController(AssemblyService service) {
        this.service = service;
    }

    @GetMapping
    public List<AssemblyResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category
    ) {
        return service.listAssemblies(query, category);
    }

    @GetMapping("/{id}")
    public AssemblyResponse get(@PathVariable UUID id) {
        return service.getAssembly(id);
    }

    @PostMapping
    public AssemblyResponse create(
            @Valid @RequestBody UpsertAssemblyRequest request,
            Authentication authentication
    ) {
        return service.createAssembly(request, authentication);
    }

    @PutMapping("/{id}")
    public AssemblyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertAssemblyRequest request
    ) {
        return service.updateAssembly(id, request);
    }

    @GetMapping("/{id}/components")
    public List<ComponentResponse> components(@PathVariable UUID id) {
        return service.components(id);
    }

    @PostMapping("/{id}/components")
    public ComponentResponse addComponent(
            @PathVariable UUID id,
            @Valid @RequestBody CreateComponentRequest request
    ) {
        return service.addComponent(id, request);
    }

    @DeleteMapping("/{id}/components/{componentId}")
    public void removeComponent(
            @PathVariable UUID id,
            @PathVariable UUID componentId
    ) {
        service.removeComponent(id, componentId);
    }

    @PostMapping("/{id}/calculate")
    public EstimateResponse calculate(
            @PathVariable UUID id,
            @Valid @RequestBody CalculateCostRequest request,
            Authentication authentication
    ) {
        return service.calculate(id, request, authentication);
    }

    @GetMapping("/{id}/estimates")
    public List<EstimateResponse> estimates(@PathVariable UUID id) {
        return service.estimates(id);
    }

    @GetMapping("/estimates/{id}")
    public EstimateResponse estimate(@PathVariable UUID id) {
        return service.estimate(id);
    }
}