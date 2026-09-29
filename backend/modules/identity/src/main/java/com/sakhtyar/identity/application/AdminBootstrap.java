package com.sakhtyar.identity.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean resetPasswordOnStartup;

    public AdminBootstrap(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Value("${app.bootstrap-admin.username}") String username,
            @Value("${app.bootstrap-admin.password}") String password,
            @Value("${app.bootstrap-admin.display-name}") String displayName,
            @Value("${app.bootstrap-admin.reset-password-on-startup:false}") boolean resetPasswordOnStartup
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.resetPasswordOnStartup = resetPasswordOnStartup;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UserEntity existingAdmin =
                repository.findByUsernameIgnoreCase(username).orElse(null);

        if (existingAdmin != null) {
            syncExistingAdmin(existingAdmin);
            return;
        }

        Instant now = Instant.now();
        UserEntity admin = new UserEntity(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(password),
                safeDisplayName(),
                null,
                null,
                UserRole.ADMIN,
                UserStatus.ACTIVE,
                now
        );

        repository.save(admin);

        auditService.recordAs(
                "USER",
                admin.getId(),
                "BOOTSTRAP_ADMIN_CREATED",
                "system",
                Map.of("username", admin.getUsername())
        );
    }

    private void syncExistingAdmin(UserEntity admin) {
        boolean changed = false;
        Instant now = Instant.now();

        if (resetPasswordOnStartup
                && !passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.changePassword(passwordEncoder.encode(password), now);
            changed = true;
        }

        String safeName = safeDisplayName();
        if (!safeName.equals(admin.getDisplayName())) {
            admin.updateOwnProfile(
                    safeName,
                    admin.getEmail(),
                    admin.getMobile()
            );
            changed = true;
        }

        if (!changed) {
            return;
        }

        repository.save(admin);

        auditService.recordAs(
                "USER",
                admin.getId(),
                "BOOTSTRAP_ADMIN_SYNCED",
                "system",
                Map.of("username", admin.getUsername())
        );
    }

    private String safeDisplayName() {
        String value = displayName == null ? "" : displayName.trim();

        if (value.isBlank()
                || value.length() > 200
                || looksCorrupted(value)) {
            return "System Administrator";
        }

        return value;
    }

    private boolean looksCorrupted(String value) {
        return value.contains("Ã")
                || value.contains("Â")
                || value.contains("Ø")
                || value.contains("Ù")
                || value.contains("Û")
                || value.contains("�")
                || value.contains("Æ");
    }
}
