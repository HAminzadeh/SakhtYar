package com.sakhtyar.material.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialPriceAggregateRepository extends JpaRepository<MaterialPriceAggregateEntity, UUID> {
    void deleteByMaterialItemId(UUID materialItemId);
    List<MaterialPriceAggregateEntity> findByMaterialItemIdOrderByCalculatedAtDesc(UUID materialItemId);
}