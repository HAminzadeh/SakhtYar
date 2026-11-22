package com.sakhtyar.casefile.application;

import static org.junit.jupiter.api.Assertions.*;

import com.sakhtyar.casefile.domain.CaseStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class Phase08CaseRevisionLifecycleTest {
    private static final Instant REVISION = Instant.parse("2026-11-22T07:30:00.123456Z");

    @Test void etagIsStableAndStrong() {
        String tag = CaseRevision.etag(REVISION);
        assertTrue(tag.startsWith("\"") && tag.endsWith("\""));
        assertDoesNotThrow(() -> CaseRevision.requireMatch(tag, REVISION));
        assertDoesNotThrow(() -> CaseRevision.requireMatch(null, REVISION));
        assertThrows(IllegalArgumentException.class, () -> CaseRevision.etag(null));
    }

    @Test void staleAndWeakTagsFailClosed() {
        String current = CaseRevision.etag(REVISION);
        for (String invalid : new String[] { "", "W/" + current,
                CaseRevision.etag(REVISION.plusNanos(1000)), "*", "junk" }) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> CaseRevision.requireMatch(invalid, REVISION));
            assertEquals(HttpStatus.PRECONDITION_FAILED, error.getStatusCode());
        }
    }

    @Test void archivedCaseCannotBeEditedOrSilentlyRestored() {
        for (CaseStatus target : CaseStatus.values()) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> CaseLifecycle.requireEditable(CaseStatus.ARCHIVED, target));
            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        }
        assertDoesNotThrow(() -> CaseLifecycle.requireEditable(
                CaseStatus.NEGOTIATION, CaseStatus.ARCHIVED));
        assertDoesNotThrow(() -> CaseLifecycle.requireEditable(
                CaseStatus.CONTRACT, CaseStatus.CONSTRUCTION));
        assertThrows(ResponseStatusException.class,
                () -> CaseLifecycle.requireEditable(CaseStatus.DRAFT, null));
    }
}
