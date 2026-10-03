package com.sakhtyar.agents.input;

import static com.sakhtyar.agents.api.InputGatewayDtos.GatewayResponse;

import com.sakhtyar.agents.input.InputGatewayKnowledgeResolver.Resolution;
import com.sakhtyar.agents.input.PersianInputCanonicalizer.Canonicalization;
import com.sakhtyar.audit.application.AuditService;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersianInputGateway {

    public static final String SCHEMA_VERSION="1.0";
    public static final String LOCALE="fa-IR";

    private final PersianInputCanonicalizer canonicalizer;
    private final InputGatewayKnowledgeResolver knowledgeResolver;
    private final PersianInputRequestRepository repository;
    private final AuditService auditService;

    public PersianInputGateway(
            PersianInputCanonicalizer canonicalizer,
            InputGatewayKnowledgeResolver knowledgeResolver,
            PersianInputRequestRepository repository,
            AuditService auditService
    ) {
        this.canonicalizer=canonicalizer;
        this.knowledgeResolver=knowledgeResolver;
        this.repository=repository;
        this.auditService=auditService;
    }

    @Transactional
    public GatewayResponse process(
            UUID caseId,
            UUID conversationId,
            String rawText,
            Map<String,Object> explicitParameters
    ) {
        String normalized=PersianInputNormalizer.normalize(rawText);

        Canonicalization canonical=
                canonicalizer.canonicalize(normalized,explicitParameters);

        Resolution resolution=knowledgeResolver.resolve(normalized,caseId);

        ArrayList<InputClarification> clarifications=
                new ArrayList<>(canonical.clarifications());

        for (String surface:resolution.ambiguousSurfaces()) {
            clarifications.add(new InputClarification(
                    "knowledgeTerm:" + PersianInputNormalizer.normalize(surface),
                    "\u0645\u0646\u0638\u0648\u0631 \u0634\u0645\u0627 \u0627\u0632 \u0627\u0635\u0637\u0644\u0627\u062D \u00AB"
                            + surface
                            + "\u00BB \u062F\u0642\u06CC\u0642\u0627\u064B \u06A9\u062F\u0627\u0645 \u0645\u0639\u0646\u06CC \u0627\u0633\u062A\u061F"
            ));
        }

        InputGatewayStatus status=clarifications.isEmpty()
                ? InputGatewayStatus.READY
                : InputGatewayStatus.NEEDS_CLARIFICATION;

        LinkedHashMap<String,Object> metadata=new LinkedHashMap<>();
        metadata.put(
                "clarifications",
                clarifications.stream()
                        .map(c->Map.<String,Object>of(
                                "key",c.key(),
                                "question",c.question()
                        ))
                        .toList()
        );
        metadata.put("knowledgeAmbiguities",resolution.ambiguousSurfaces());
        metadata.put("rawLength",rawText==null?0:rawText.length());
        metadata.put("normalizedLength",normalized.length());

        Instant now=Instant.now();

        PersianInputRequestEntity entity=new PersianInputRequestEntity(
                UUID.randomUUID(),
                caseId,
                conversationId,
                rawText==null?"":rawText,
                normalized,
                LOCALE,
                status,
                SCHEMA_VERSION,
                PersianInputNormalizer.VERSION,
                canonical.parameters(),
                resolution.recognizedTerms(),
                metadata,
                currentActor(),
                now
        );

        repository.save(entity);

        LinkedHashMap<String,Object> audit=new LinkedHashMap<>();
        audit.put("status",status.name());
        audit.put("normalizerVersion",PersianInputNormalizer.VERSION);
        audit.put("recognizedTermCount",resolution.recognizedTerms().size());
        audit.put("clarificationCount",clarifications.size());
        if (caseId!=null) audit.put("caseId",caseId.toString());
        if (conversationId!=null) audit.put("conversationId",conversationId.toString());

        auditService.record(
                "PERSIAN_INPUT_REQUEST",
                entity.getId(),
                "PERSIAN_INPUT_NORMALIZED",
                audit
        );

        return toResponse(entity,clarifications);
    }

    @Transactional(readOnly=true)
    public GatewayResponse get(UUID id) {
        PersianInputRequestEntity entity=repository.findById(id)
                .orElseThrow(()->new IllegalArgumentException(
                        "Persian input request not found."
                ));

        return toResponse(
                entity,
                decodeClarifications(entity.getMetadata())
        );
    }

    @Transactional(readOnly=true)
    public List<GatewayResponse> byCase(UUID caseId) {
        return repository.findByCaseIdOrderByCreatedAtDesc(caseId)
                .stream()
                .map(e->toResponse(
                        e,
                        decodeClarifications(e.getMetadata())
                ))
                .toList();
    }

    private GatewayResponse toResponse(
            PersianInputRequestEntity entity,
            List<InputClarification> clarifications
    ) {
        return new GatewayResponse(
                entity.getId(),
                entity.getCaseId(),
                entity.getConversationId(),
                entity.getRawText(),
                entity.getNormalizedText(),
                entity.getLocale(),
                entity.getStatus(),
                entity.getSchemaVersion(),
                entity.getNormalizerVersion(),
                entity.getCanonicalParameters(),
                entity.getRecognizedTerms(),
                clarifications,
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    private List<InputClarification> decodeClarifications(
            Map<String,Object> metadata
    ) {
        Object raw=metadata.get("clarifications");
        if (!(raw instanceof List<?> list)) return List.of();

        ArrayList<InputClarification> result=new ArrayList<>();

        for (Object item:list) {
            if (!(item instanceof Map<?,?> map)) continue;

            Object key=map.get("key");
            Object question=map.get("question");

            if (key!=null && question!=null) {
                result.add(new InputClarification(
                        String.valueOf(key),
                        String.valueOf(question)
                ));
            }
        }

        return List.copyOf(result);
    }

    private String currentActor() {
        Authentication authentication=
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication==null
                || !authentication.isAuthenticated()
                || authentication.getName()==null) {
            return "system";
        }

        return authentication.getName();
    }
}