package com.sakhtyar.scenario.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface QualityPackageRepository extends JpaRepository<QualityPackageEntity,UUID>{Optional<QualityPackageEntity> findByCodeIgnoreCase(String code);List<QualityPackageEntity> findAllByOrderByUpdatedAtDesc();}