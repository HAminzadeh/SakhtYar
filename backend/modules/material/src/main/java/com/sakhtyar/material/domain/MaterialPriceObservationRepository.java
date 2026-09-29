package com.sakhtyar.material.domain;

import com.sakhtyar.knowledge.domain.KnowledgeReviewStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialPriceObservationRepository extends JpaRepository<MaterialPriceObservationEntity, UUID> {
    List<MaterialPriceObservationEntity> findByMaterialItemIdOrderByObservedAtDesc(UUID materialItemId);
    List<MaterialPriceObservationEntity> findByMaterialItemIdAndReviewStatusOrderByObservedAtAsc(
            UUID materialItemId, KnowledgeReviewStatus reviewStatus
    );
}