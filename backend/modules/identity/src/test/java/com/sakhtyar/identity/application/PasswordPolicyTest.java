package com.sakhtyar.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        policy.validate("StrongPass_123");
    }

    @Test
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> policy.validate("1234567890"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
