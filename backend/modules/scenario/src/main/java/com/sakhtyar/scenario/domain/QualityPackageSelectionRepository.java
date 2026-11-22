package com.sakhtyar.scenario.domain;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QualityPackageSelectionRepository extends JpaRepository<QualityPackageSelectionEntity, UUID> {
    List<QualityPackageSelectionEntity> findByPackageIdOrderBySortOrderAscIdAsc(UUID packageId);

    Optional<QualityPackageSelectionEntity> findByPackageIdAndSlotCodeIgnoreCase(UUID packageId, String slotCode);
}
