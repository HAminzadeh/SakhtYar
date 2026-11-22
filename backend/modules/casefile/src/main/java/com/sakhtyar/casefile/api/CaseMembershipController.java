package com.sakhtyar.casefile.api;

import com.sakhtyar.casefile.application.CaseMembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/members")
public final class CaseMembershipController {
    private final CaseMembershipService membership;
    public CaseMembershipController(CaseMembershipService membership) { this.membership=membership; }
    public record GrantRequest(@NotBlank String username) {}

    @GetMapping public List<Map<String,Object>> list(@PathVariable UUID caseId) {
        return membership.list(caseId);
    }
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(@PathVariable UUID caseId, @Valid @RequestBody GrantRequest request) {
        membership.grant(caseId,request.username());
    }
    @DeleteMapping("/{username}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID caseId, @PathVariable String username) {
        membership.revoke(caseId,username);
    }
}
