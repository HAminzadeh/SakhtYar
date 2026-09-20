package com.sakhtyar.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 254)
    private String email;

    @Column(length = 30)
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public UserEntity(
            UUID id,
            String username,
            String passwordHash,
            String displayName,
            String email,
            String mobile,
            UserRole role,
            UserStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.mobile = mobile;
        this.role = role;
        this.status = status;
        this.active = status == UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = null;
        this.passwordChangedAt = createdAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void registerFailedLogin(int maxAttempts, Instant lockUntil) {
        this.failedLoginAttempts += 1;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = lockUntil;
        }
        this.updatedAt = Instant.now();
    }

    public void loginSucceeded(Instant now) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public void changePassword(String encodedPassword, Instant now) {
        this.passwordHash = encodedPassword;
        this.passwordChangedAt = now;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void updateOwnProfile(
            String displayName,
            String email,
            String mobile
    ) {
        this.displayName = displayName;
        this.email = email;
        this.mobile = mobile;
        this.updatedAt = Instant.now();
    }

    public void adminUpdate(
            String displayName,
            String email,
            String mobile,
            UserRole role,
            UserStatus status
    ) {
        this.displayName = displayName;
        this.email = email;
        this.mobile = mobile;
        this.role = role;
        this.status = status;
        this.active = status == UserStatus.ACTIVE;
        if (status != UserStatus.ACTIVE) {
            this.lockedUntil = null;
            this.failedLoginAttempts = 0;
        }
        this.updatedAt = Instant.now();
    }

    public void unlock() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = Instant.now();
    }

    public boolean isTemporarilyLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isEnabledForLogin() {
        return active
                && status == UserStatus.ACTIVE
                && !isTemporarilyLocked();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getMobile() { return mobile; }
    public UserRole getRole() { return role; }
    public boolean isActive() { return active; }
    public UserStatus getStatus() { return status; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
