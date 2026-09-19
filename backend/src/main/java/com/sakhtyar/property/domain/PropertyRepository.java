package com.sakhtyar.property.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<PropertyEntity, UUID> {

    Optional<PropertyEntity> findByCaseId(UUID caseId);

    boolean existsByCaseId(UUID caseId);
}
