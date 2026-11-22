package com.sakhtyar.audit.api;

import com.sakhtyar.audit.domain.AuditEventEntity;
import com.sakhtyar.audit.domain.AuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only, bounded, read-only metadata view. Never exports payload_text. */
@RestController
public class AuditReadController {
    private static final Pattern TRACE = Pattern.compile("\\\"traceId\\\"\\s*:\\s*\\\"([0-9a-f]{16}|[0-9a-f]{32})\\\"");
    private final AuditEventRepository repository;

    public AuditReadController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/v1/admin/audit/events")
    @Transactional(readOnly = true)
    public Page<AuditEventView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) UUID aggregateId,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities() == null
                || authentication.getAuthorities().stream().noneMatch(
                        a -> "AUDIT_READ".equals(a.getAuthority()))
                || authentication.getAuthorities().stream().anyMatch(
                        a -> "ROLE_TESTER".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit access denied");
        }
        if (page < 0 || page > 1000 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audit page bounds");
        }
        boolean hasType = aggregateType != null && !aggregateType.isBlank();
        boolean hasId = aggregateId != null;
        if (hasType != hasId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit filter requires type and id");
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<AuditEventEntity> events = hasType
                ? repository.findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
                        validateAggregateType(aggregateType), aggregateId, pageable)
                : repository.findAllByOrderByCreatedAtDesc(pageable);
        return events.map(AuditReadController::toView);
    }

    private static String validateAggregateType(String value) {
        if (value.length() > 100 || !value.matches("[A-Za-z0-9_.:-]{1,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid aggregate type");
        }
        return value;
    }

    private static AuditEventView toView(AuditEventEntity event) {
        String traceId = null;
        String text = event.getPayloadText();
        if (text != null && text.length() <= 4096) {
            Matcher matcher = TRACE.matcher(text);
            if (matcher.find()) traceId = matcher.group(1);
        }
        return new AuditEventView(event.getId(), event.getAggregateType(),
                event.getAggregateId(), event.getAction(), event.getActor(),
                event.getCreatedAt(), traceId);
    }

    /** Never include payload_text, arbitrary exception strings, or raw request fields. */
    public record AuditEventView(UUID id, String aggregateType, UUID aggregateId,
                                 String action, String actor, Instant createdAt, String traceId) { }
}
