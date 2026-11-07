package com.sakhtyar.quality;

/** Non-secret isolation facts. A caller must actually measure them; booleans are not proof by themselves. */
public record IsolationEvidence(boolean restrictedPrincipal, boolean syntheticOnly,
                                boolean externalEffectsDenied, boolean noRealAccess,
                                String databasePrincipal, String evidenceSha256) {
    public IsolationEvidence {
        if (databasePrincipal == null || databasePrincipal.isBlank() ||
            evidenceSha256 == null || !evidenceSha256.matches("(?i)[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Isolation evidence requires a principal and a real evidence reference");
        }
    }
}
