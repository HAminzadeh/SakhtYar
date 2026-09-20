package com.sakhtyar.auth.api;

import com.sakhtyar.identity.domain.Permission;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record CreateUserRequest(
            @NotBlank
            @Pattern(regexp = "[\\p{L}\\p{N}._-]{3,100}")
            String username,

            @NotBlank
            @Size(min = 2, max = 200)
            String displayName,

            @Email
            @Size(max = 254)
            String email,

            @Size(max = 30)
            String mobile,

            @NotBlank
            @Size(min = 10, max = 100)
            String password,

            @NotNull UserRole role,

            @NotNull UserStatus status
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank
            @Size(min = 2, max = 200)
            String displayName,

            @Email
            @Size(max = 254)
            String email,

            @Size(max = 30)
            String mobile,

            @NotNull UserRole role,

            @NotNull UserStatus status
    ) {
    }

    public record AdminResetPasswordRequest(
            @NotBlank
            @Size(min = 10, max = 100)
            String newPassword
    ) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String displayName,
            String email,
            String mobile,
            UserRole role,
            UserStatus status,
            Set<String> permissions,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastLoginAt,
            Instant passwordChangedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static UserResponse from(UserEntity user) {
            Set<String> permissions = user.getRole()
                    .permissions()
                    .stream()
                    .map(Permission::name)
                    .collect(Collectors.toUnmodifiableSet());

            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getMobile(),
                    user.getRole(),
                    user.getStatus(),
                    permissions,
                    user.getFailedLoginAttempts(),
                    user.getLockedUntil(),
                    user.getLastLoginAt(),
                    user.getPasswordChangedAt(),
                    user.getCreatedAt(),
                    user.getUpdatedAt()
            );
        }
    }
}
