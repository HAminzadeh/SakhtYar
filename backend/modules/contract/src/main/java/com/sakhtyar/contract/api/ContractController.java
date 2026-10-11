package com.sakhtyar.contract.api;

import static com.sakhtyar.contract.api.ContractDtos.*;
import com.sakhtyar.contract.application.ContractService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {
  private final ContractService service;
  public ContractController(ContractService service){this.service=service;}

  @GetMapping("/{id}") public ContractResponse get(@PathVariable UUID id){return service.get(id);}
  @GetMapping("/cases/{caseId}") public List<ContractResponse> byCase(@PathVariable UUID caseId){return service.byCase(caseId);}
  @PostMapping("/cases/{caseId}") public ContractResponse create(@PathVariable UUID caseId,@Valid @RequestBody UpsertContractRequest r,Authentication a){return service.create(caseId,r,a);}
  @PutMapping("/{id}") public ContractResponse update(@PathVariable UUID id,@Valid @RequestBody UpsertContractRequest r){return service.update(id,r);}
  @GetMapping("/cases/{caseId}/review") public ContractReviewResponse review(@PathVariable UUID caseId){return service.review(caseId);}
}
