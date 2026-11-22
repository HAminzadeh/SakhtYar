package com.sakhtyar.owner.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Phase10OwnershipFractionsTest {
    @Test void thirdsAndSeventhsAreExactRatherThanRounded() {
        assertFalse(OwnershipFractions.exceedsWhole(List.of(
                new OwnershipFractions.Share(1, 3), new OwnershipFractions.Share(2, 3))));
        List<OwnershipFractions.Share> sevenths = new ArrayList<>();
        for (int i = 0; i < 7; i++) sevenths.add(new OwnershipFractions.Share(1, 7));
        assertFalse(OwnershipFractions.exceedsWhole(sevenths));
    }
    @Test void overAllocationIsAlwaysDetected() {
        assertTrue(OwnershipFractions.exceedsWhole(List.of(
                new OwnershipFractions.Share(1, 2),
                new OwnershipFractions.Share(1, 3),
                new OwnershipFractions.Share(1, 4))));
    }
    @Test void largeDenominatorsRemainExact() {
        assertFalse(OwnershipFractions.exceedsWhole(List.of(
                new OwnershipFractions.Share(1, Integer.MAX_VALUE),
                new OwnershipFractions.Share(Integer.MAX_VALUE - 1, Integer.MAX_VALUE))));
        assertTrue(OwnershipFractions.exceedsWhole(List.of(
                new OwnershipFractions.Share(1, Integer.MAX_VALUE),
                new OwnershipFractions.Share(Integer.MAX_VALUE, Integer.MAX_VALUE))));
    }
    @Test void invalidFractionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OwnershipFractions.Share(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new OwnershipFractions.Share(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new OwnershipFractions.Share(3, 2));
        assertFalse(OwnershipFractions.exceedsWhole(List.of()));
    }
}
