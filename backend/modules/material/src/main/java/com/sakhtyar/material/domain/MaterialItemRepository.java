package com.sakhtyar.material.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialItemRepository extends JpaRepository<MaterialItemEntity, UUID> {
    Optional<MaterialItemEntity> findByCodeIgnoreCase(String code);
    List<MaterialItemEntity> findAllByOrderByUpdatedAtDesc();
}