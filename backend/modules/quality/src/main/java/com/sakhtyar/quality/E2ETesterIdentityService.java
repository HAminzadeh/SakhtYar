package com.sakhtyar.quality;

import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ensures the existing application's least-privilege tester identity; no alternate user store. */
@Service
public class E2ETesterIdentityService {
    public static final String USERNAME = "tester";
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final UserDetailsService details;
    private final SecureRandom random = new SecureRandom();

    public E2ETesterIdentityService(UserRepository users, PasswordEncoder encoder, UserDetailsService details) {
        this.users = users;
        this.encoder = encoder;
        this.details = details;
    }

    @Transactional
    public Identity ensure() {
        UserEntity account = users.findByUsernameIgnoreCase(USERNAME).orElse(null);
        boolean created = false;
        if (account == null) {
            byte[] bytes = new byte[36];
            random.nextBytes(bytes);
            String oneTimeUnpublishedPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            try {
                account = users.saveAndFlush(new UserEntity(
                    UUID.randomUUID(), USERNAME, encoder.encode(oneTimeUnpublishedPassword),
                    "E2E Tester", null, null, UserRole.TESTER, UserStatus.ACTIVE, Instant.now()));
                created = true;
            } finally {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
        }
        if (account.getRole() != UserRole.TESTER || !account.isEnabledForLogin()) {
            throw new IllegalStateException("E2E tester username conflicts with a non-TESTER or inactive account; no automatic privilege changes");
        }
        return new Identity(account.getId(), account.getUsername(), created);
    }

    /** Load authorities through the *same* user details service used by application login. */
    public UserDetails loadAuthorities() {
        UserDetails user = details.loadUserByUsername(USERNAME);
        boolean safe = user.isEnabled() && user.getAuthorities().stream().anyMatch(a -> "ROLE_TESTER".equals(a.getAuthority()))
            && user.getAuthorities().stream().noneMatch(a -> "USER_MANAGE".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
        if (!safe) throw new IllegalStateException("E2E tester authorities do not meet least-privilege policy");
        return user;
    }

    public record Identity(UUID id, String username, boolean created) {}
}
