package com.sakhtyar.casefile.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {
    List<CaseEntity> findAllByOrderByUpdatedAtDesc();

    // Serialize writes to the same case without changing the existing database schema.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CaseEntity c where c.id = :id")
    Optional<CaseEntity> findByIdForUpdate(@Param("id") UUID id);

    // Apply REAL scope, creator and active membership filters in PostgreSQL before materializing rows.
    @Query(value = """
            SELECT c.* FROM construction_case c
            WHERE c.data_scope = 'REAL' AND (
              :isAdmin = true OR lower(c.created_by) = lower(:username)
              OR EXISTS (SELECT 1 FROM construction_case_access m
                         WHERE m.case_id = c.id AND m.active = true
                           AND lower(m.username) = lower(:username))
            )
            ORDER BY c.updated_at DESC
            """, nativeQuery = true)
    List<CaseEntity> findVisibleRealCases(@Param("username") String username,
                                          @Param("isAdmin") boolean isAdmin);
}
