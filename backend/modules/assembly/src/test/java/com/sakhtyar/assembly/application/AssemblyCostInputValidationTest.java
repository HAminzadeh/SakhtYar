package com.sakhtyar.assembly.application;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssemblyCostInputValidationTest {
    @Test
    void negativeInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            AssemblyCostCalculator.effectiveQuantity(BigDecimal.ONE, new BigDecimal("-0.1"), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () ->
            AssemblyCostCalculator.lineTotal(BigDecimal.ONE, new BigDecimal("-5")));
    }
}
