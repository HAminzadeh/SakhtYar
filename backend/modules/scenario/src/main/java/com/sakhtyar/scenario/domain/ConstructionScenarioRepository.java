package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ConstructionScenarioRepository extends JpaRepository<ConstructionScenarioEntity,UUID>{Optional<ConstructionScenarioEntity> findByCodeIgnoreCase(String code);List<ConstructionScenarioEntity> findAllByOrderByUpdatedAtDesc();
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select s from ConstructionScenarioEntity s where s.id=:id")
 Optional<ConstructionScenarioEntity> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
}