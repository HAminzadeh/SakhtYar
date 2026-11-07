package com.sakhtyar.quality;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A strictly in-memory TEST canary for Redis/MinIO namespace contracts.
 * Never opens a socket or writes a production bucket/key. Not a production adapter.
 * Real adapters remain BLOCKED until endpoint IAM/policies and TEST isolation are proven.
 */
public final class Phase02ScopedFakeStorage {
    public enum Service { REDIS, MINIO }
    private record Stored(byte[] value, Instant expiresAt) {
        Stored { value = value.clone(); }
    }
    private final ConcurrentHashMap<String, Stored> values = new ConcurrentHashMap<>();
    private final Clock clock;

    public Phase02ScopedFakeStorage() { this(Clock.systemUTC()); }
    public Phase02ScopedFakeStorage(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public String put(FixtureContext fixture, Service service, String logicalKey,
                      byte[] content, Duration ttl) {
        if (content == null || content.length > 1_048_576 || ttl == null ||
            ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("TEST canary payload/TTL is invalid");
        String key = key(fixture, service, logicalKey);
        Stored candidate = new Stored(content, clock.instant().plus(ttl));
        values.compute(key, (ignored, current) -> {
            if (current != null && current.expiresAt().isAfter(clock.instant()) &&
                !Arrays.equals(current.value(), candidate.value()))
                throw new SecurityException("Conflicting TEST canary key; overwrite denied");
            return candidate;
        });
        return key;
    }

    public byte[] read(FixtureContext fixture, Service service, String logicalKey) {
        Stored found = values.get(key(fixture, service, logicalKey));
        if (found == null) return null;
        if (!found.expiresAt().isAfter(clock.instant())) {
            values.remove(key(fixture, service, logicalKey), found);
            return null;
        }
        return found.value().clone();
    }

    /** Deletes only one exact run-owned key; never supports wildcard/bucket cleanup. */
    public boolean delete(FixtureContext fixture, Service service, String logicalKey) {
        return values.remove(key(fixture, service, logicalKey)) != null;
    }

    /** Not exposed as a network/storage enumeration API. Used only by synthetic tests. */
    public int totalCanaryKeys() { return values.size(); }

    private static String key(FixtureContext fixture, Service service, String logicalKey) {
        Objects.requireNonNull(fixture, "TEST fixture required");
        Objects.requireNonNull(service, "TEST service required");
        Phase02SandboxNamespaces ns = new Phase02SandboxNamespaces(fixture.workspaceId(), fixture.runId());
        String key = switch (service) {
            case REDIS -> ns.redisKey(logicalKey);
            case MINIO -> ns.minioKey(logicalKey);
        };
        if (!(service == Service.REDIS ? ns.ownsRedisKey(key) : ns.ownsMinioKey(key)))
            throw new SecurityException("Key does not belong to TEST run");
        return service.name() + ":" + key;
    }
}
