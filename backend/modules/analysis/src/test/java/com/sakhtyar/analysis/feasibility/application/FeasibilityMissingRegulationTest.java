package com.sakhtyar.analysis.feasibility.application;

import com.sakhtyar.analysis.feasibility.domain.FeasibilityStatus;
import com.sakhtyar.scenario.domain.ScenarioStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeasibilityMissingRegulationTest {
    @Test
    void missingVerifiedRegulationCannotClaimFeasible() {
        var result = new FeasibilityCalculator().calculate(new FeasibilityCalculator.Input(
            null, ScenarioStatus.READY, new BigDecimal("200"),
            new BigDecimal("600"), new BigDecimal("9000000000"), 5));
        assertEquals(FeasibilityStatus.INSUFFICIENT_DATA, result.status());
        assertTrue(result.reasons().stream().anyMatch(x -> "REGULATION_EVIDENCE_MISSING".equals(x.code())));
    }
}
