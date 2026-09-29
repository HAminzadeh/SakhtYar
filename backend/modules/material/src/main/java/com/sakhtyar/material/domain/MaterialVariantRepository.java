package com.sakhtyar.material.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialVariantRepository extends JpaRepository<MaterialVariantEntity, UUID> {
    List<MaterialVariantEntity> findByMaterialItemIdOrderByCreatedAtDesc(UUID materialItemId);
}