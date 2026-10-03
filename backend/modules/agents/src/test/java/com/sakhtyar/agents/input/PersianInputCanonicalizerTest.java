package com.sakhtyar.agents.input;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersianInputCanonicalizerTest {

    private final PersianInputCanonicalizer canonicalizer =
            new PersianInputCanonicalizer();

    @Test
    void extractsAreaFrontageAndPassageWidth() {
        String text=PersianInputNormalizer.normalize(
                "\u0632\u0645\u06CC\u0646 \u06F1\u06F2\u06F0\u06F0 \u0645\u062A\u0631 \u0645\u0631\u0628\u0639"
                + " \u0628\u0631 \u06F2\u06F0 \u0645\u062A\u0631"
                + " \u06AF\u0630\u0631 \u06F1\u06F2 \u0645\u062A\u0631\u06CC"
        );

        var result=canonicalizer.canonicalize(
                text,
                Map.of()
        );

        assertEquals(
                new BigDecimal("1200"),
                result.parameters().get("landAreaM2")
        );
        assertEquals(
                new BigDecimal("20"),
                result.parameters().get("frontageM")
        );
        assertEquals(
                new BigDecimal("12"),
                result.parameters().get("passageWidthM")
        );
    }

    @Test
    void asksForCurrencyWhenMonetaryNumberHasNoUnit() {
        String text=PersianInputNormalizer.normalize(
                "\u0642\u06CC\u0645\u062A \u06F1\u06F0\u06F0\u06F0"
        );

        var result=canonicalizer.canonicalize(
                text,
                Map.of()
        );

        assertEquals(
                "currencyUnit",
                result.clarifications().getFirst().key()
        );
    }
}