package com.sakhtyar.knowledge.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.knowledge.api.KnowledgeDtos.*;
import com.sakhtyar.knowledge.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {

    private final KnowledgeTermRepository termRepository;
    private final KnowledgeTermAliasRepository aliasRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgeCandidateRepository candidateRepository;
    private final KnowledgeTermRevisionRepository revisionRepository;
    private final AuditService auditService;

    public KnowledgeService(
            KnowledgeTermRepository termRepository,
            KnowledgeTermAliasRepository aliasRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgeCandidateRepository candidateRepository,
            KnowledgeTermRevisionRepository revisionRepository,
            AuditService auditService
    ) {
        this.termRepository = termRepository;
        this.aliasRepository = aliasRepository;
        this.relationRepository = relationRepository;
        this.candidateRepository = candidateRepository;
        this.revisionRepository = revisionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TermResponse> listTerms(String query, String domain, KnowledgeReviewStatus status) {
        String q = KnowledgeTextNormalizer.normalize(query);
        return termRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(t -> domain == null || domain.isBlank() || t.getDomain().equalsIgnoreCase(domain))
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> q.isBlank()
                        || KnowledgeTextNormalizer.normalize(t.getCode()).contains(q)
                        || KnowledgeTextNormalizer.normalize(t.getNameFa()).contains(q)
                        || KnowledgeTextNormalizer.normalize(t.getNameEn()).contains(q))
                .map(TermResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TermResponse getTerm(UUID id) {
        return TermResponse.from(requireTerm(id));
    }

    @Transactional
    public TermResponse createTerm(UpsertTermRequest request, Authentication authentication) {
        String actor = actor(authentication);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        termRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Knowledge term code already exists: " + code);
        });

        Instant now = Instant.now();
        KnowledgeTermEntity entity = new KnowledgeTermEntity(
                UUID.randomUUID(), code, request.domain().trim(), trim(request.category()),
                request.nameFa().trim(), trim(request.nameEn()), trim(request.definition()),
                trim(request.unitCode()), request.status(), request.confidence(),
                request.sourceId(), trim(request.provenanceUrl()), request.metadata(), actor, now
        );
        termRepository.save(entity);
        saveRevision(entity, 1, defaultReason(request.changeReason(), "Term created"), actor, now);

        auditService.record("KNOWLEDGE_TERM", entity.getId(), "KNOWLEDGE_TERM_CREATED",
                Map.of("code", entity.getCode(), "status", entity.getStatus().name()));

        return TermResponse.from(entity);
    }

    @Transactional
    public TermResponse updateTerm(UUID id, UpsertTermRequest request, Authentication authentication) {
        String actor = actor(authentication);
        KnowledgeTermEntity entity = requireTerm(id);

        if (!entity.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new IllegalArgumentException("Knowledge term code is immutable after creation.");
        }

        Instant now = Instant.now();
        entity.update(
                request.domain().trim(), trim(request.category()), request.nameFa().trim(),
                trim(request.nameEn()), trim(request.definition()), trim(request.unitCode()),
                request.status(), request.confidence(), request.sourceId(),
                trim(request.provenanceUrl()), request.metadata(), actor, now
        );

        int nextRevision = revisionRepository.findTopByTermIdOrderByRevisionNoDesc(id)
                .map(r -> r.getRevisionNo() + 1)
                .orElse(1);
        saveRevision(entity, nextRevision, defaultReason(request.changeReason(), "Term updated"), actor, now);

        auditService.record("KNOWLEDGE_TERM", entity.getId(), "KNOWLEDGE_TERM_UPDATED",
                Map.of("code", entity.getCode(), "status", entity.getStatus().name(), "revision", nextRevision));

        return TermResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<AliasResponse> listAliases(UUID termId) {
        requireTerm(termId);
        return aliasRepository.findByTermIdOrderByAliasAsc(termId).stream()
                .map(AliasResponse::from)
                .toList();
    }

    @Transactional
    public AliasResponse addAlias(UUID termId, CreateAliasRequest request, Authentication authentication) {
        requireTerm(termId);
        String normalized = KnowledgeTextNormalizer.normalize(request.alias());
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be blank after normalization.");
        }

        KnowledgeTermAliasEntity entity = new KnowledgeTermAliasEntity(
                UUID.randomUUID(), termId, request.locale().trim().toLowerCase(Locale.ROOT),
                request.alias().trim(), normalized, request.aliasType(),
                request.status() == null ? KnowledgeReviewStatus.APPROVED : request.status(),
                request.confidence(), request.sourceId(), actor(authentication), Instant.now()
        );
        aliasRepository.save(entity);

        auditService.record("KNOWLEDGE_TERM", termId, "KNOWLEDGE_ALIAS_ADDED",
                Map.of("alias", entity.getAlias(), "type", entity.getAliasType().name()));

        return AliasResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<RelationResponse> listRelations(UUID termId) {
        requireTerm(termId);
        return relationRepository.findByFromTermIdOrToTermId(termId, termId).stream()
                .map(RelationResponse::from)
                .toList();
    }

    @Transactional
    public RelationResponse addRelation(CreateRelationRequest request, Authentication authentication) {
        requireTerm(request.fromTermId());
        requireTerm(request.toTermId());
        if (request.fromTermId().equals(request.toTermId())) {
            throw new IllegalArgumentException("A knowledge term cannot relate to itself.");
        }

        KnowledgeRelationEntity entity = new KnowledgeRelationEntity(
                UUID.randomUUID(), request.fromTermId(), request.relationType(), request.toTermId(),
                request.status() == null ? KnowledgeReviewStatus.APPROVED : request.status(),
                request.sourceId(), request.metadata(), actor(authentication), Instant.now()
        );
        relationRepository.save(entity);

        auditService.record("KNOWLEDGE_RELATION", entity.getId(), "KNOWLEDGE_RELATION_CREATED",
                Map.of("type", entity.getRelationType().name()));

        return RelationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<CandidateResponse> listCandidates(KnowledgeReviewStatus status) {
        List<KnowledgeCandidateEntity> items = status == null
                ? candidateRepository.findAllByOrderByCreatedAtDesc()
                : candidateRepository.findByStatusOrderByCreatedAtAsc(status);
        return items.stream().map(CandidateResponse::from).toList();
    }

    @Transactional
    public CandidateResponse createCandidate(CreateCandidateRequest request, Authentication authentication) {
        Instant now = Instant.now();
        KnowledgeCandidateEntity entity = new KnowledgeCandidateEntity(
                UUID.randomUUID(), request.candidateType(), request.origin(), request.rawInput().trim(),
                request.normalizedPayload(), trim(request.proposedTermCode()),
                trim(request.proposedNameFa()), trim(request.proposedNameEn()),
                request.sourceId(), trim(request.sourceUrl()), request.confidence(),
                trim(request.reason()), actor(authentication), now
        );
        candidateRepository.save(entity);

        auditService.record("KNOWLEDGE_CANDIDATE", entity.getId(), "KNOWLEDGE_CANDIDATE_CREATED",
                Map.of("origin", entity.getOrigin().name(), "type", entity.getCandidateType().name()));

        return CandidateResponse.from(entity);
    }

    @Transactional
    public CandidateResponse reviewCandidate(
            UUID id, ReviewCandidateRequest request, Authentication authentication
    ) {
        KnowledgeCandidateEntity entity = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge candidate not found."));

        String reviewer = actor(authentication);
        entity.review(request.status(), trim(request.note()), reviewer, Instant.now());

        auditService.record("KNOWLEDGE_CANDIDATE", entity.getId(), "KNOWLEDGE_CANDIDATE_REVIEWED",
                Map.of("status", entity.getStatus().name(), "reviewedBy", reviewer));

        return CandidateResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<RevisionResponse> revisions(UUID termId) {
        requireTerm(termId);
        return revisionRepository.findByTermIdOrderByRevisionNoDesc(termId).stream()
                .map(RevisionResponse::from)
                .toList();
    }

    private KnowledgeTermEntity requireTerm(UUID id) {
        return termRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge term not found."));
    }

    private void saveRevision(
            KnowledgeTermEntity term, int revisionNo, String reason, String actor, Instant now
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", term.getCode());
        snapshot.put("domain", term.getDomain());
        snapshot.put("category", term.getCategory());
        snapshot.put("nameFa", term.getNameFa());
        snapshot.put("nameEn", term.getNameEn());
        snapshot.put("definition", term.getDefinition());
        snapshot.put("unitCode", term.getUnitCode());
        snapshot.put("status", term.getStatus().name());
        snapshot.put("confidence", term.getConfidence());
        snapshot.put("sourceId", term.getSourceId());
        snapshot.put("provenanceUrl", term.getProvenanceUrl());
        snapshot.put("metadata", term.getMetadata());

        revisionRepository.save(new KnowledgeTermRevisionEntity(
                UUID.randomUUID(), term.getId(), revisionNo, snapshot, reason, actor, now
        ));
    }

    private static String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String defaultReason(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}