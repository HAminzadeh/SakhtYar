package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class T10RuntimeRoleGuardTest {
    @Test void disabledDoesNotQueryDatabase() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        new T10RuntimeRoleGuard(db, false).run(null);
        verifyNoInteractions(db);
    }
    @Test void rejectsSuperuserOrBypassRls() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        assertThrows(IllegalStateException.class,
            () -> new T10RuntimeRoleGuard(db, true).run(null));
    }
    @Test void rejectsUnknownRole() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Boolean.class))).thenReturn(null);
        assertThrows(IllegalStateException.class,
            () -> new T10RuntimeRoleGuard(db, true).run(null));
    }
    @Test void allowsNonPrivilegedRuntime() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        assertDoesNotThrow(() -> new T10RuntimeRoleGuard(db, true).run(null));
    }
}
