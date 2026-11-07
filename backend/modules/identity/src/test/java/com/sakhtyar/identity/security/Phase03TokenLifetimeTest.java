package com.sakhtyar.identity.security;

import com.sakhtyar.identity.application.AuthSessionService;
import com.sakhtyar.identity.domain.AuthSessionRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class Phase03TokenLifetimeTest {
    private static final String SECRET = "a-very-long-development-secret-that-is-definitely-more-than-32-chars";
    @Test void rejectsNonPositiveAndExcessiveJwtLifetime() {
        for (long minutes : new long[]{-1,0,1441,Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new JwtService(SECRET,minutes));
        assertDoesNotThrow(() -> new JwtService(SECRET,30));
    }
    @Test void rejectsInvalidRefreshLifetimeBeforeAnyDatabaseConnection() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        for (long days : new long[]{-1,0,91,Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new AuthSessionService(repository,days));
        assertDoesNotThrow(() -> new AuthSessionService(repository,30));
    }
}
