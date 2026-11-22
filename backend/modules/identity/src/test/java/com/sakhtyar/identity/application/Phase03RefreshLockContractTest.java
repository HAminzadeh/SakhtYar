package com.sakhtyar.identity.application;

import com.sakhtyar.identity.domain.AuthSessionRepository;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import static org.junit.jupiter.api.Assertions.*;

class Phase03RefreshLockContractTest {
    @Test void refreshTokenLookupIsSerializedInPostgresTransaction() throws Exception {
        Method method=AuthSessionRepository.class.getMethod("findByRefreshTokenHash",String.class);
        Lock lock=method.getAnnotation(Lock.class);
        assertNotNull(lock,"refresh/revoke token lookup must acquire a DB row lock");
        assertEquals(LockModeType.PESSIMISTIC_WRITE,lock.value());
    }
}
