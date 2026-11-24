package com.sakhtyar.builder.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface BuilderProfileRepository extends JpaRepository<BuilderProfileEntity,UUID>{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BuilderProfileEntity b where b.id = :id")
    Optional<BuilderProfileEntity> findByIdForUpdate(@Param("id") UUID id);
    List<BuilderProfileEntity> findAllByOrderByUpdatedAtDesc();
    List<BuilderProfileEntity> findByVerifiedTrueOrderByUpdatedAtDesc();
    Page<BuilderProfileEntity> findByVerifiedTrue(Pageable pageable);
    Page<BuilderProfileEntity> findByVerifiedTrueAndCityIgnoreCase(String city,Pageable pageable);
    Page<BuilderProfileEntity> findByCityIgnoreCase(String city,Pageable pageable);
    boolean existsByNationalIdentifierIgnoreCase(String nationalIdentifier);
    boolean existsByNationalIdentifierIgnoreCaseAndIdNot(String nationalIdentifier,UUID id);
}
