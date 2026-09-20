package com.sakhtyar.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserRolePermissionTest {

    @Test
    void adminHasEveryPermission() {
        assertThat(UserRole.ADMIN.permissions())
                .containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void readOnlyCannotWrite() {
        assertThat(UserRole.READ_ONLY.has(Permission.CASE_READ)).isTrue();
        assertThat(UserRole.READ_ONLY.has(Permission.CASE_WRITE)).isFalse();
        assertThat(UserRole.READ_ONLY.has(Permission.USER_MANAGE)).isFalse();
    }
}
