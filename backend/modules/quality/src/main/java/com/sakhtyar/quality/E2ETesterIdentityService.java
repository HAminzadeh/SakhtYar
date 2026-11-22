package com.sakhtyar.quality;

import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Existing application user-store only; no grants, password rotation or bypass. */
@Service
public class E2ETesterIdentityService {
    public static final String USERNAME = "tester";
    private static final long ADVISORY_LOCK_KEY = 91827364501234L;
    private static final Set<String> REQUIRED_AUTHORITIES =
        Set.of("ROLE_TESTER", "QUALITY_VIEW", "QUALITY_RUN_SAFE");
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final UserDetailsService details;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public E2ETesterIdentityService(UserRepository users, PasswordEncoder encoder,
                                    UserDetailsService details, JdbcTemplate jdbc) {
        this.users = users;
        this.encoder = encoder;
        this.details = details;
        this.jdbc = jdbc;
    }

    /** Must execute in the caller's PostgreSQL transaction for the advisory lock to remain held. */
    @Transactional
    public Identity ensure() {
        // Serializes concurrent E2E-start requests across application instances.
        jdbc.queryForList("select pg_advisory_xact_lock(?)", ADVISORY_LOCK_KEY);
        UserEntity account = users.findByUsernameIgnoreCase(USERNAME).orElse(null);
        boolean created = false;
        if (account == null) {
            byte[] passwordMaterial = new byte[48];
            random.nextBytes(passwordMaterial);
            try {
                String generatedPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(passwordMaterial);
                account = users.saveAndFlush(new UserEntity(
                    UUID.randomUUID(), USERNAME, encoder.encode(generatedPassword),
                    "E2E Tester", null, null, UserRole.TESTER, UserStatus.ACTIVE, Instant.now()));
                created = true;
            } finally {
                Arrays.fill(passwordMaterial, (byte) 0);
            }
        }
        if (account.getRole() != UserRole.TESTER || account.getStatus() != UserStatus.ACTIVE ||
            !account.isEnabledForLogin()) {
            throw new IllegalStateException("Tester conflict: account is inactive, locked or not TESTER; manual review required");
        }
        return new Identity(account.getId(), account.getUsername(), created);
    }

    public UserDetails loadAuthorities() {
        UserDetails user = details.loadUserByUsername(USERNAME);
        Set<String> actual = user.getAuthorities().stream()
            .map(a -> a.getAuthority()).collect(Collectors.toSet());
        if (!user.isEnabled() || !USERNAME.equalsIgnoreCase(user.getUsername()) ||
            !REQUIRED_AUTHORITIES.equals(actual)) {
            throw new IllegalStateException("E2E tester has unexpected role or privileges");
        }
        return user;
    }

    public record Identity(UUID id, String username, boolean created) {
        public String action() { return created ? "CREATED" : "REUSED"; }
    }
}
