package com.sakhtyar.finance.application;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinancialParticipantValidationTest {
    @Test
    void duplicateParticipantKeysAreRejected() {
        var input = new FinancialCalculator.Input(BigDecimal.ONE, BigDecimal.TEN,
            BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var participants = List.of(
            new FinancialCalculator.Participant("owner", new BigDecimal("50"), new BigDecimal("50")),
            new FinancialCalculator.Participant("owner", new BigDecimal("50"), new BigDecimal("50")));
        assertThrows(IllegalArgumentException.class,
            () -> new FinancialCalculator().calculate(input, participants));
    }
}
