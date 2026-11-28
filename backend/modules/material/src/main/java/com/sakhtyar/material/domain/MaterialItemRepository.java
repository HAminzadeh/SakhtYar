package com.sakhtyar.material.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MaterialItemRepository extends JpaRepository<MaterialItemEntity, UUID> {
    Optional<MaterialItemEntity> findByCodeIgnoreCase(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MaterialItemEntity m where m.id = :id")
    Optional<MaterialItemEntity> findLockedById(@Param("id") UUID id);
    List<MaterialItemEntity> findAllByOrderByUpdatedAtDesc();
}