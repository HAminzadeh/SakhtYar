package com.sakhtyar.casefile.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Optional strong HTTP precondition for case updates; backward-compatible with
 * clients that do not yet send If-Match. PostgreSQL timestamps are microsecond
 * precision, so case writes normalize their updatedAt values to microseconds.
 */
public final class CaseRevision {
    private CaseRevision() { }

    public static String etag(Instant updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException("Case revision timestamp is required.");
        }
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(
                updatedAt.toString().getBytes(StandardCharsets.UTF_8));
        return "\"" + value + "\"";
    }

    public static void requireMatch(String ifMatch, Instant updatedAt) {
        // Existing clients remain compatible; clients opting in to optimistic
        // checks receive HTTP 412 after a stale GET. Never treat weak ETags as strong.
        if (ifMatch == null) {
            return;
        }
        if (ifMatch.isBlank() || !etag(updatedAt).equals(ifMatch)) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Case has changed. Reload before editing.");
        }
    }
}
