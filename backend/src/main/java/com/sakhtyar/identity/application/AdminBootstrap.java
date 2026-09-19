package com.sakhtyar.identity.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
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

    public AdminBootstrap(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Value("${app.bootstrap-admin.username}") String username,
            @Value("${app.bootstrap-admin.password}") String password,
            @Value("${app.bootstrap-admin.display-name}") String displayName
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.existsByUsernameIgnoreCase(username)) {
            return;
        }

        UserEntity admin = new UserEntity(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(password),
                displayName,
                UserRole.ADMIN,
                true,
                Instant.now()
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
}
