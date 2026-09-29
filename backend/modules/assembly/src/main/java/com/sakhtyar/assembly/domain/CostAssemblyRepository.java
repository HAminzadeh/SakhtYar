package com.sakhtyar.assembly.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostAssemblyRepository extends JpaRepository<CostAssemblyEntity, UUID> {
    Optional<CostAssemblyEntity> findByCodeIgnoreCase(String code);
    List<CostAssemblyEntity> findAllByOrderByUpdatedAtDesc();
}