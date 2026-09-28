package com.sakhtyar.auth.api;

import com.sakhtyar.identity.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank
            @Pattern(
                    regexp = "[\\p{L}\\p{N}._-]{3,100}",
                    message = "Ù†Ø§Ù… Ú©Ø§Ø±Ø¨Ø±ÛŒ Ø¨Ø§ÛŒØ¯ Û³ ØªØ§ Û±Û°Û° Ú©Ø§Ø±Ø§Ú©ØªØ± Ùˆ Ø¨Ø¯ÙˆÙ† ÙØ§ØµÙ„Ù‡ Ø¨Ø§Ø´Ø¯."
            )
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
            String password
    ) {
    }

    public record ProfileUpdateRequest(
            @NotBlank
            @Size(min = 2, max = 200)
            String displayName,

            @Email
            @Size(max = 254)
            String email,

            @Size(max = 30)
            String mobile
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,

            @NotBlank
            @Size(min = 10, max = 100)
            String newPassword
    ) {
    }

    public record MobileLoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            @Size(max = 200) String deviceName
    ) {
    }

    public record MobileRefreshRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record MobileLogoutRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record RegistrationResponse(
            UUID id,
            String username,
            String displayName,
            String status,
            String message
    ) {
    }

    public record MeResponse(
            UUID id,
            String username,
            String displayName,
            String email,
            String mobile,
            String role,
            UserStatus status,
            Set<String> permissions,
            Instant lastLoginAt
    ) {
        
    }

    public record MobileTokenResponse(
            String tokenType,
            String accessToken,
            long accessExpiresInSeconds,
            String refreshToken,
            Instant refreshExpiresAt,
            MeResponse user
    ) {
    }

    public record SessionResponse(
            UUID id,
            String clientType,
            String deviceName,
            String userAgent,
            String ipAddress,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant revokedAt,
            boolean active
    ) {
    }

    public record CsrfResponse(
            String headerName,
            String parameterName,
            String token
    ) {
    }
}

