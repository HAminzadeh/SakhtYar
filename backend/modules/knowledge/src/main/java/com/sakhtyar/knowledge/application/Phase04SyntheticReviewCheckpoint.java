package com.sakhtyar.knowledge.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Synthetic-only, in-memory contract for review conflict and checkpoint replay.
 * Not wired to production persistence. Restart persistence remains an open gate.
 */
public final class Phase04SyntheticReviewCheckpoint {
    public enum State { PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
    public record ReviewEvent(long version, String actor, String action, String hash) {}
    public record Checkpoint(UUID workspace, UUID run, String stage, int attempts,
                             State state, int completedUniqueItems) {}
    private record RunKey(UUID workspace, UUID run, String stage) {}
    private final Map<RunKey, Checkpoint> checkpoints = new HashMap<>();
    private final Map<RunKey, Set<String>> processed = new HashMap<>();
    private final Map<UUID, ReviewEvent> reviews = new HashMap<>();

    public synchronized ReviewEvent review(UUID sourceId, long expectedVersion,
                                           String submitter, String reviewer, String action,
                                           String previousHash, String newHash) {
        if (sourceId == null || submitter == null || reviewer == null ||
            submitter.equalsIgnoreCase(reviewer) || !Set.of("APPROVE", "REJECT").contains(action) ||
            !isHash(previousHash) || !isHash(newHash))
            throw new SecurityException("Invalid synthetic review provenance or separation of duties");
        ReviewEvent existing = reviews.get(sourceId);
        long current = existing == null ? 0 : existing.version();
        if (current != expectedVersion) throw new IllegalStateException("Review version conflict");
        ReviewEvent next = new ReviewEvent(current + 1, reviewer, action,
                hash(sourceId + ":" + current + ":" + previousHash + ":" + newHash + ":" + action));
        reviews.put(sourceId, next);
        return next;
    }

    public synchronized Checkpoint open(UUID workspace, UUID run, String stage) {
        RunKey key = key(workspace, run, stage);
        return checkpoints.computeIfAbsent(key,
                k -> new Checkpoint(workspace, run, stage, 0, State.PENDING, 0));
    }
    public synchronized Checkpoint start(UUID workspace, UUID run, String stage, int maxAttempts) {
        RunKey key = key(workspace, run, stage);
        Checkpoint cp = open(workspace, run, stage);
        if (cp.state() == State.CANCELLED || cp.state() == State.COMPLETED ||
            cp.attempts() >= maxAttempts || maxAttempts < 1)
            throw new IllegalStateException("Checkpoint not restartable");
        return set(key, cp.attempts() + 1, State.RUNNING);
    }
    public synchronized Checkpoint record(UUID workspace, UUID run, String stage, String itemKey) {
        RunKey key = key(workspace, run, stage);
        Checkpoint cp = open(workspace, run, stage);
        if (cp.state() != State.RUNNING || itemKey == null || !itemKey.matches("[a-zA-Z0-9._-]{1,128}"))
            throw new SecurityException("Invalid checkpoint item or state");
        processed.computeIfAbsent(key, k -> new HashSet<>()).add(itemKey);
        return set(key, cp.attempts(), State.RUNNING);
    }
    public synchronized Checkpoint transition(UUID workspace, UUID run, String stage, State to) {
        RunKey key = key(workspace, run, stage);
        Checkpoint cp = open(workspace, run, stage);
        if (to == null || cp.state() == State.CANCELLED || cp.state() == State.COMPLETED ||
            (to == State.COMPLETED && cp.state() != State.RUNNING) ||
            (to == State.PAUSED && cp.state() != State.RUNNING))
            throw new IllegalStateException("Illegal checkpoint transition");
        return set(key, cp.attempts(), to);
    }
    private Checkpoint set(RunKey key, int attempts, State state) {
        Checkpoint cp = new Checkpoint(key.workspace(), key.run(), key.stage(), attempts, state,
                processed.getOrDefault(key, Set.of()).size());
        checkpoints.put(key, cp);
        return cp;
    }
    private static RunKey key(UUID workspace, UUID run, String stage) {
        Objects.requireNonNull(workspace); Objects.requireNonNull(run);
        if (stage == null || !stage.matches("[A-Z_]{2,50}"))
            throw new SecurityException("Invalid checkpoint stage");
        return new RunKey(workspace, run, stage);
    }
    private static boolean isHash(String h) { return h != null && h.matches("(?i)[a-f0-9]{64}"); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
