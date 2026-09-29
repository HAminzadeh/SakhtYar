package com.sakhtyar.assembly.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.sakhtyar.assembly.domain.PriceBasis;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AssemblyCostCalculatorTest {

    @Test
    void appliesWasteAndRequestedQuantity() {
        BigDecimal result = AssemblyCostCalculator.effectiveQuantity(
                new BigDecimal("2.5"),
                new BigDecimal("0.10"),
                new BigDecimal("4")
        );

        assertEquals(new BigDecimal("11.000000"), result);
    }

    @Test
    void choosesConfiguredPriceBasis() {
        assertEquals(
                new BigDecimal("120"),
                AssemblyCostCalculator.choosePrice(
                        PriceBasis.MEDIAN,
                        new BigDecimal("80"),
                        new BigDecimal("160"),
                        new BigDecimal("125"),
                        new BigDecimal("120")
                )
        );
    }

    @Test
    void calculatesRoundedLineTotal() {
        assertEquals(
                new BigDecimal("37.03"),
                AssemblyCostCalculator.lineTotal(
                        new BigDecimal("3.333333"),
                        new BigDecimal("11.11")
                )
        );
    }
}