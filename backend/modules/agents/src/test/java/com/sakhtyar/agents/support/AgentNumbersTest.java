package com.sakhtyar.agents.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AgentNumbersTest {

    @Test
    void convertsPercentToRatio() {
        assertEquals(
                new BigDecimal("0.60000000"),
                AgentNumbers.normalizeRatio(new BigDecimal("60"))
        );
    }

    @Test
    void keepsExistingRatio() {
        assertEquals(
                new BigDecimal("0.60"),
                AgentNumbers.normalizeRatio(new BigDecimal("0.60"))
        );
    }
}
