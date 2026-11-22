package com.sakhtyar.builder.api;
import static com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.builder.application.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/builders")
public class BuilderController {
  private final BuilderService service;
  private final BuilderMatchingService matching;

  public BuilderController(BuilderService service,BuilderMatchingService matching){this.service=service;this.matching=matching;}

  @GetMapping public List<BuilderResponse> list(){return service.list();}
  @GetMapping("/{id}") public BuilderResponse get(@PathVariable UUID id){return service.get(id);}
  @PostMapping public BuilderResponse create(@Valid @RequestBody UpsertBuilderRequest r){return service.create(r);}
  @PutMapping("/{id}") public BuilderResponse update(@PathVariable UUID id,@Valid @RequestBody UpsertBuilderRequest r){return service.update(id,r);}
  @GetMapping("/cases/{caseId}") public List<ProjectBuilderResponse> byCase(@PathVariable UUID caseId){return service.byCase(caseId);}
  @PostMapping("/cases/{caseId}") public ProjectBuilderResponse link(@PathVariable UUID caseId,@Valid @RequestBody LinkBuilderRequest r){return service.link(caseId,r);}

  @PostMapping("/cases/{caseId}/matches")
  public MatchResponse matches(@PathVariable UUID caseId,@RequestBody(required=false) MatchRequest r){
    return matching.match(caseId,r==null?null:r.requiredCapital(),r==null||r.limit()==null?10:r.limit());
  }
}
