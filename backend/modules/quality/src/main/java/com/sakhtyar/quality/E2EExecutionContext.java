package com.sakhtyar.quality;

import com.sakhtyar.identity.domain.UserRole;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable execution metadata; only cancellation token is a separately synchronized handle. */
public record E2EExecutionContext(
    UUID runId,
    UUID workspaceId,
    String requestedBy,
    UUID testerUserId,
    String testerUsername,
    UserRole testerRole,
    Set<String> authorities,
    String gitCommit,
    AtomicBoolean cancellation,
    String traceId
) {
    public E2EExecutionContext {
        if (runId == null || workspaceId == null || testerUserId == null ||
            requestedBy == null || requestedBy.isBlank() ||
            testerUsername == null || !"tester".equalsIgnoreCase(testerUsername) ||
            testerRole != UserRole.TESTER || authorities == null ||
            !authorities.equals(Set.of("ROLE_TESTER", "QUALITY_VIEW", "QUALITY_RUN_SAFE")) ||
            cancellation == null || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Incomplete or overprivileged E2E execution context");
        }
        authorities = Set.copyOf(authorities);
        gitCommit = gitCommit == null || gitCommit.isBlank() ? "unknown" : gitCommit;
    }

    public void requireNotCancelled() {
        if (cancellation.get() || Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("E2E worker cancellation requested");
        }
    }
}
