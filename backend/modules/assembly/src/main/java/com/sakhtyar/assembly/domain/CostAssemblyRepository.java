package com.sakhtyar.assembly.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostAssemblyRepository extends JpaRepository<CostAssemblyEntity, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from CostAssemblyEntity a where a.id=:id")
    Optional<CostAssemblyEntity> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<CostAssemblyEntity> findByCodeIgnoreCase(String code);
    List<CostAssemblyEntity> findAllByOrderByUpdatedAtDesc();
}