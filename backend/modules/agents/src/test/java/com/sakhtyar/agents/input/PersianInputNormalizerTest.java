package com.sakhtyar.agents.input;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PersianInputNormalizerTest {

    @Test
    void normalizesArabicCharactersDigitsAndHalfSpace() {
        String input="\u0643\u064A\u0627\u0646\u067E\u0627\u0631\u0633\u200C\u06F1\u06F2\u06F3";

        assertEquals(
                "\u06A9\u06CC\u0627\u0646\u067E\u0627\u0631\u0633 123",
                PersianInputNormalizer.normalize(input)
        );
    }

    @Test
    void removesThousandsSeparatorBetweenDigits() {
        assertEquals(
                "1250000",
                PersianInputNormalizer.normalize(
                        "\u06F1\u066C\u06F2\u06F5\u06F0\u066C\u06F0\u06F0\u06F0"
                )
        );
    }

    @Test
    void normalizesArabicDecimalSeparator() {
        assertEquals(
                "12.5",
                PersianInputNormalizer.normalize(
                        "\u06F1\u06F2\u066B\u06F5"
                )
        );
    }
}