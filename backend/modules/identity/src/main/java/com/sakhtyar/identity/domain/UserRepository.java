package com.sakhtyar.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<UserEntity> findAllByOrderByCreatedAtDesc();

    long countByRoleAndStatus(UserRole role, UserStatus status);

    // Serialize failed-login accounting for the same user (one PostgreSQL row).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where lower(u.username) = lower(:username)")
    Optional<UserEntity> findByUsernameIgnoreCaseForUpdate(@Param("username") String username);

    // Serialize last-active-admin changes across distinct admin user rows.
    // Always lock the same complete set in deterministic primary-key order.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.role = :role and u.status = :status order by u.id asc")
    List<UserEntity> lockUsersByRoleAndStatus(@Param("role") UserRole role,
                                             @Param("status") UserStatus status);
}
