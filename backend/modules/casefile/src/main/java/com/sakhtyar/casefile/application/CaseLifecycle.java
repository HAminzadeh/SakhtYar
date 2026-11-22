package com.sakhtyar.casefile.application;

import com.sakhtyar.casefile.domain.CaseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimal safe lifecycle policy: preserve historic status transitions but make
 * archival terminal for ordinary PUT. Restoration requires a separately reviewed
 * business action; do not silently reactivate archived cases.
 */
public final class CaseLifecycle {
    private CaseLifecycle() { }

    public static void requireEditable(CaseStatus current, CaseStatus target) {
        if (current == null || target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid case status is required.");
        }
        if (current == CaseStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Archived cases are read-only.");
        }
    }
}
