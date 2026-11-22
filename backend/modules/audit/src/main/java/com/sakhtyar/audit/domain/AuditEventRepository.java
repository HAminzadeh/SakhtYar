package com.sakhtyar.audit.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    // Bounded reads; never project payload_text into a public response.
    Page<AuditEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType, UUID aggregateId, Pageable pageable);
}
