package com.sakhtyar.audit.application;

import com.sakhtyar.audit.domain.AuditEventEntity;
import com.sakhtyar.audit.domain.AuditEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditEventRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String aggregateType,
            UUID aggregateId,
            String action,
            Map<String, ?> payload
    ) {
        recordAs(
                aggregateType,
                aggregateId,
                action,
                currentActor(),
                payload
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(
            String aggregateType,
            UUID aggregateId,
            String action,
            String actor,
            Map<String, ?> payload
    ) {
        repository.save(
                new AuditEventEntity(
                        UUID.randomUUID(),
                        aggregateType,
                        aggregateId,
                        action,
                        actor,
                        toJson(payload),
                        Instant.now()
                )
        );
    }

    private String currentActor() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }

        return authentication.getName();
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(
                    payload == null ? Map.of() : payload
            );
        } catch (JacksonException e) {
            return "{\"serializationError\":true}";
        }
    }
}
