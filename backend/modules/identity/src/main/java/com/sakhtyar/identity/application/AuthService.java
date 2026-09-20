package com.sakhtyar.identity.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.auth.api.AuthDtos.ProfileUpdateRequest;
import com.sakhtyar.auth.api.AuthDtos.RegisterRequest;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;
    private final String dummyHash;

    public AuthService(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            AuditService auditService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("dummy-password-not-used");
    }

    @Transactional
    public UserEntity register(RegisterRequest request) {
        String username = request.username().trim();
        String email = clean(request.email());

        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "این نام کاربری قبلاً ثبت شده است."
            );
        }

        if (email != null && repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "این ایمیل قبلاً ثبت شده است."
            );
        }

        passwordPolicy.validate(request.password());

        Instant now = Instant.now();
        UserEntity user = new UserEntity(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                email,
                clean(request.mobile()),
                UserRole.READ_ONLY,
                UserStatus.PENDING,
                now
        );

        repository.save(user);

        auditService.recordAs(
                "USER",
                user.getId(),
                "USER_REGISTERED",
                username,
                Map.of(
                        "username", username,
                        "status", UserStatus.PENDING.name()
                )
        );

        return user;
    }

    @Transactional
    public UserEntity authenticate(String username, String password) {
        String normalized = username == null ? "" : username.trim();

        UserEntity user = repository
                .findByUsernameIgnoreCase(normalized)
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(
                    password == null ? "" : password,
                    dummyHash
            );
            throw invalidCredentials();
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "حساب شما هنوز توسط مدیر سیستم تأیید نشده است."
            );
        }

        if (user.getStatus() == UserStatus.SUSPENDED || !user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "حساب کاربری غیرفعال است."
            );
        }

        if (user.isTemporarilyLocked()) {
            throw new ResponseStatusException(
                    HttpStatus.LOCKED,
                    "حساب به‌صورت موقت قفل شده است. کمی بعد دوباره تلاش کنید."
            );
        }

        boolean matches = passwordEncoder.matches(
                password == null ? "" : password,
                user.getPasswordHash()
        );

        if (!matches) {
            Instant lockUntil = Instant.now()
                    .plus(LOCK_MINUTES, ChronoUnit.MINUTES);

            user.registerFailedLogin(MAX_FAILED_ATTEMPTS, lockUntil);
            repository.save(user);

            auditService.recordAs(
                    "USER",
                    user.getId(),
                    "LOGIN_FAILED",
                    user.getUsername(),
                    Map.of("attempts", user.getFailedLoginAttempts())
            );

            if (user.isTemporarilyLocked()) {
                throw new ResponseStatusException(
                        HttpStatus.LOCKED,
                        "به دلیل تلاش‌های ناموفق، حساب برای ۱۵ دقیقه قفل شد."
                );
            }

            throw invalidCredentials();
        }

        Instant now = Instant.now();
        user.loginSucceeded(now);
        repository.save(user);

        auditService.recordAs(
                "USER",
                user.getId(),
                "LOGIN_SUCCEEDED",
                user.getUsername(),
                Map.of()
        );

        return user;
    }

    @Transactional(readOnly = true)
    public UserEntity requireByUsername(String username) {
        return repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "کاربر معتبر نیست."
                ));
    }

    @Transactional(readOnly = true)
    public UserEntity requireById(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "کاربر معتبر نیست."
                ));
    }

    @Transactional
    public UserEntity updateProfile(
            String username,
            ProfileUpdateRequest request
    ) {
        UserEntity user = requireByUsername(username);
        String email = clean(request.email());

        if (email != null) {
            repository.findByEmailIgnoreCase(email)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "این ایمیل قبلاً استفاده شده است."
                        );
                    });
        }

        user.updateOwnProfile(
                request.displayName().trim(),
                email,
                clean(request.mobile())
        );
        repository.save(user);

        auditService.record(
                "USER",
                user.getId(),
                "PROFILE_UPDATED",
                Map.of()
        );

        return user;
    }

    @Transactional
    public void changePassword(
            String username,
            String currentPassword,
            String newPassword
    ) {
        UserEntity user = requireByUsername(username);

        if (!passwordEncoder.matches(
                currentPassword,
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "رمز عبور فعلی صحیح نیست."
            );
        }

        if (passwordEncoder.matches(
                newPassword,
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "رمز عبور جدید باید با رمز فعلی متفاوت باشد."
            );
        }

        passwordPolicy.validate(newPassword);

        user.changePassword(
                passwordEncoder.encode(newPassword),
                Instant.now()
        );
        repository.save(user);

        auditService.record(
                "USER",
                user.getId(),
                "PASSWORD_CHANGED",
                Map.of()
        );
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "نام کاربری یا رمز عبور صحیح نیست."
        );
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
