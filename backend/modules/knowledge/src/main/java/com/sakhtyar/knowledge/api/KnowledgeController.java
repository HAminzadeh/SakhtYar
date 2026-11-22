package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.api.KnowledgeDtos.*;
import com.sakhtyar.knowledge.application.KnowledgeService;
import com.sakhtyar.knowledge.domain.KnowledgeReviewStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/terms")
    public List<TermResponse> listTerms(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) KnowledgeReviewStatus status
    ) {
        return service.listTerms(query, domain, status);
    }

    @GetMapping("/terms/{id}")
    public TermResponse getTerm(@PathVariable UUID id) {
        return service.getTerm(id);
    }

    @PostMapping("/terms")
    public TermResponse createTerm(
            @Valid @RequestBody UpsertTermRequest request,
            Authentication authentication
    ) {
        return service.createTerm(request, authentication);
    }

    @PutMapping("/terms/{id}")
    public TermResponse updateTerm(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertTermRequest request,
            Authentication authentication
    ) {
        return service.updateTerm(id, request, authentication);
    }

    @GetMapping("/terms/{id}/aliases")
    public List<AliasResponse> listAliases(@PathVariable UUID id) {
        return service.listAliases(id);
    }

    @PostMapping("/terms/{id}/aliases")
    public AliasResponse addAlias(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAliasRequest request,
            Authentication authentication
    ) {
        return service.addAlias(id, request, authentication);
    }

    @GetMapping("/terms/{id}/relations")
    public List<RelationResponse> listRelations(@PathVariable UUID id) {
        return service.listRelations(id);
    }

    @PostMapping("/relations")
    public RelationResponse addRelation(
            @Valid @RequestBody CreateRelationRequest request,
            Authentication authentication
    ) {
        return service.addRelation(request, authentication);
    }

    @GetMapping("/candidates")
    public List<CandidateResponse> listCandidates(
            @RequestParam(required = false) KnowledgeReviewStatus status
    ) {
        return service.listCandidates(status);
    }

    @PostMapping("/candidates")
    public CandidateResponse createCandidate(
            @Valid @RequestBody CreateCandidateRequest request,
            Authentication authentication
    ) {
        return service.createCandidate(request, authentication);
    }

    @PostMapping("/candidates/{id}/review")
    public CandidateResponse reviewCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewCandidateRequest request,
            Authentication authentication
    ) {
        return service.reviewCandidate(id, request, authentication);
    }

    @GetMapping("/terms/{id}/revisions")
    public List<RevisionResponse> revisions(@PathVariable UUID id) {
        return service.revisions(id);
    }
}