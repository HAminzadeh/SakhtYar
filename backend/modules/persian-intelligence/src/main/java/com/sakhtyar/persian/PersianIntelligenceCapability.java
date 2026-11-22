package com.sakhtyar.persian;

public record PersianIntelligenceCapability(
        String name,
        Status status,
        String detail
) {
    public enum Status { READY, MISSING, FAILED }
}