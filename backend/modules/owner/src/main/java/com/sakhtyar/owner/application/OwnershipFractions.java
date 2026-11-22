package com.sakhtyar.owner.application;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Objects;

/**
 * Exact sums of positive ownership fractions. Never round shares before
 * enforcing the invariant that a property's total ownership is <= 1.
 */
public final class OwnershipFractions {
    private OwnershipFractions() { }

    public record Share(int numerator, int denominator) {
        public Share {
            if (numerator <= 0 || denominator <= 0 || numerator > denominator) {
                throw new IllegalArgumentException("Ownership fraction must be between zero and one.");
            }
        }
    }

    public static boolean exceedsWhole(Collection<Share> shares) {
        Objects.requireNonNull(shares, "shares");
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;
        for (Share share : shares) {
            Objects.requireNonNull(share, "share");
            BigInteger partDenominator = BigInteger.valueOf(share.denominator());
            numerator = numerator.multiply(partDenominator)
                    .add(BigInteger.valueOf(share.numerator()).multiply(denominator));
            denominator = denominator.multiply(partDenominator);
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
            if (numerator.compareTo(denominator) > 0) return true;
        }
        return false;
    }
}
