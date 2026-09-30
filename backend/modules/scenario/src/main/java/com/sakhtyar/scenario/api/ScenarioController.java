package com.sakhtyar.scenario.api;
import static com.sakhtyar.scenario.api.ScenarioDtos.*; import com.sakhtyar.scenario.application.ScenarioService; import jakarta.validation.Valid; import java.util.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/scenarios")
public class ScenarioController {
 private final ScenarioService service; public ScenarioController(ScenarioService service){this.service=service;}
 @GetMapping("/quality-packages") public List<PackageResponse> packages(){return service.packages();}
 @PostMapping("/quality-packages") public PackageResponse createPackage(@Valid @RequestBody UpsertPackageRequest r,Authentication a){return service.createPackage(r,a);}
 @PutMapping("/quality-packages/{id}") public PackageResponse updatePackage(@PathVariable UUID id,@Valid @RequestBody UpsertPackageRequest r){return service.updatePackage(id,r);}
 @GetMapping("/quality-packages/{id}/selections") public List<SelectionResponse> selections(@PathVariable UUID id){return service.selections(id);}
 @PostMapping("/quality-packages/{id}/selections") public SelectionResponse addSelection(@PathVariable UUID id,@Valid @RequestBody CreateSelectionRequest r){return service.addSelection(id,r);}
 @DeleteMapping("/quality-packages/{id}/selections/{selectionId}") public void removeSelection(@PathVariable UUID id,@PathVariable UUID selectionId){service.removeSelection(id,selectionId);}
 @GetMapping public List<ScenarioResponse> scenarios(){return service.scenarios();}
 @PostMapping public ScenarioResponse create(@Valid @RequestBody UpsertScenarioRequest r,Authentication a){return service.createScenario(r,a);}
 @PutMapping("/{id}") public ScenarioResponse update(@PathVariable UUID id,@Valid @RequestBody UpsertScenarioRequest r){return service.updateScenario(id,r);}
 @PostMapping("/{id}/status") public ScenarioResponse status(@PathVariable UUID id,@Valid @RequestBody ChangeStatusRequest r){return service.changeStatus(id,r);}
 @GetMapping("/{id}/items") public List<ScenarioItemResponse> items(@PathVariable UUID id){return service.items(id);}
 @PostMapping("/{id}/items") public ScenarioItemResponse addItem(@PathVariable UUID id,@Valid @RequestBody CreateScenarioItemRequest r){return service.addItem(id,r);}
 @DeleteMapping("/{id}/items/{itemId}") public void removeItem(@PathVariable UUID id,@PathVariable UUID itemId){service.removeItem(id,itemId);}
 @PostMapping("/{id}/calculate") public SnapshotResponse calculate(@PathVariable UUID id,@Valid @RequestBody CalculateScenarioRequest r,Authentication a){return service.calculate(id,r,a);}
 @GetMapping("/{id}/snapshots") public List<SnapshotResponse> snapshots(@PathVariable UUID id){return service.snapshots(id);}
}