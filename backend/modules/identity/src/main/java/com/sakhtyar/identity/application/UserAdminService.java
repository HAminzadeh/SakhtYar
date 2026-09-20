package com.sakhtyar.identity.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.auth.api.UserAdminDtos.CreateUserRequest;
import com.sakhtyar.auth.api.UserAdminDtos.UpdateUserRequest;
import com.sakhtyar.auth.api.UserAdminDtos.UserResponse;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAdminService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthSessionService sessionService;
    private final AuditService auditService;

    public UserAdminService(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            AuthSessionService sessionService,
            AuditService auditService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        String email = clean(request.email());

        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "این نام کاربری قبلاً وجود دارد."
            );
        }

        if (email != null && repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "این ایمیل قبلاً وجود دارد."
            );
        }

        passwordPolicy.validate(request.password());

        UserEntity user = new UserEntity(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                email,
                clean(request.mobile()),
                request.role(),
                request.status(),
                Instant.now()
        );

        repository.save(user);

        auditService.record(
                "USER",
                user.getId(),
                "USER_CREATED_BY_ADMIN",
                Map.of(
                        "role", user.getRole().name(),
                        "status", user.getStatus().name()
                )
        );

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(
            UUID userId,
            UpdateUserRequest request,
            String currentAdminUsername
    ) {
        UserEntity user = require(userId);
        String email = clean(request.email());

        if (email != null) {
            repository.findByEmailIgnoreCase(email)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "این ایمیل قبلاً استفاده شده است."
                        );
                    });
        }

        protectLastAdmin(user, request.role(), request.status());

        if (user.getUsername().equalsIgnoreCase(currentAdminUsername)
                && request.status() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "نمی‌توانید حساب فعلی خودتان را غیرفعال کنید."
            );
        }

        user.adminUpdate(
                request.displayName().trim(),
                email,
                clean(request.mobile()),
                request.role(),
                request.status()
        );
        repository.save(user);

        if (request.status() != UserStatus.ACTIVE) {
            sessionService.revokeAll(user.getId());
        }

        auditService.record(
                "USER",
                user.getId(),
                "USER_UPDATED_BY_ADMIN",
                Map.of(
                        "role", user.getRole().name(),
                        "status", user.getStatus().name()
                )
        );

        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        UserEntity user = require(userId);
        passwordPolicy.validate(newPassword);

        user.changePassword(
                passwordEncoder.encode(newPassword),
                Instant.now()
        );
        repository.save(user);
        sessionService.revokeAll(user.getId());

        auditService.record(
                "USER",
                user.getId(),
                "PASSWORD_RESET_BY_ADMIN",
                Map.of()
        );
    }

    @Transactional
    public void unlock(UUID userId) {
        UserEntity user = require(userId);
        user.unlock();
        repository.save(user);

        auditService.record(
                "USER",
                user.getId(),
                "USER_UNLOCKED_BY_ADMIN",
                Map.of()
        );
    }

    private void protectLastAdmin(
            UserEntity user,
            UserRole nextRole,
            UserStatus nextStatus
    ) {
        boolean currentlyActiveAdmin =
                user.getRole() == UserRole.ADMIN
                        && user.getStatus() == UserStatus.ACTIVE;

        boolean remainsActiveAdmin =
                nextRole == UserRole.ADMIN
                        && nextStatus == UserStatus.ACTIVE;

        if (currentlyActiveAdmin
                && !remainsActiveAdmin
                && repository.countByRoleAndStatus(
                        UserRole.ADMIN,
                        UserStatus.ACTIVE
                ) <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "حداقل یک مدیر فعال باید در سیستم باقی بماند."
            );
        }
    }

    private UserEntity require(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "کاربر پیدا نشد."
                ));
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
