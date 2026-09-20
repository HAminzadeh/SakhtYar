package com.sakhtyar.identity.application;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public void validate(String password) {
        if (password == null || password.length() < 10 || password.length() > 100) {
            throw new IllegalArgumentException(
                    "رمز عبور باید بین ۱۰ تا ۱۰۰ کاراکتر باشد."
            );
        }

        boolean letter = password.codePoints().anyMatch(Character::isLetter);
        boolean digit = password.codePoints().anyMatch(Character::isDigit);
        boolean symbol = password.codePoints().anyMatch(
                value -> !Character.isLetterOrDigit(value)
                        && !Character.isWhitespace(value)
        );

        if (!letter || !digit || !symbol) {
            throw new IllegalArgumentException(
                    "رمز عبور باید حداقل شامل حرف، عدد و یک نویسه خاص باشد."
            );
        }
    }
}
