package com.sakhtyar.owner.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<OwnerEntity, UUID> {

    List<OwnerEntity> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

    Optional<OwnerEntity> findByIdAndPropertyId(UUID id, UUID propertyId);

    List<OwnerEntity> findByPropertyIdAndPrimaryContactTrue(UUID propertyId);

    boolean existsByPropertyIdAndNationalIdIgnoreCase(UUID propertyId, String nationalId);

    boolean existsByPropertyIdAndNationalIdIgnoreCaseAndIdNot(
            UUID propertyId,
            String nationalId,
            UUID id
    );
}
