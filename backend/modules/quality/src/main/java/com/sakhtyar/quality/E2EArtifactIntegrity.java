package com.sakhtyar.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Local TEST evidence only. Verifies on-disk bytes, not a claimed SHA-looking string.
 * The evidence root must be an explicitly configured, isolated, non-shared directory.
 */
public final class E2EArtifactIntegrity {
    private static final Set<String> TYPES = Set.of("httpPositive", "httpNegative", "database", "browser");
    private static final long MAX_BYTES = 1024 * 1024;

    private E2EArtifactIntegrity() {}

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static Path namespace(Path root, UUID workspaceId, UUID runId, String scenarioId) {
        if (root == null || workspaceId == null || runId == null || scenarioId == null ||
            !scenarioId.matches("E2E-(0[1-9]|1[0-9]|2[0-4])")) {
            throw new IllegalArgumentException("Invalid TEST evidence namespace");
        }
        return root.toAbsolutePath().normalize().resolve("evidence")
            .resolve(workspaceId.toString()).resolve(runId.toString()).resolve(scenarioId);
    }

    private static void requireNoSymbolicLinks(Path root, Path target, boolean file) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("TEST evidence root is not an existing directory");
        if (!target.startsWith(normalizedRoot)) throw new IOException("Evidence path escapes root");
        Path current = normalizedRoot;
        for (Path part : normalizedRoot.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links forbidden in evidence namespace");
        }
        if (!file && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Evidence namespace is not a directory");
    }

    /**
     * Write redacted text only; the caller must not provide raw HTTP headers, cookies or PII.
     * File is created exclusively (never overwrites an existing artifact).
     */
    public static Map<String, String> writeRedactedText(Path root, UUID workspaceId, UUID runId,
            String scenarioId, String type, String text) throws IOException {
        if (!TYPES.contains(type) || text == null) throw new IllegalArgumentException("Invalid evidence type/text");
        Path directory = namespace(root, workspaceId, runId, scenarioId);
        Path base = root.toAbsolutePath().normalize();
        requireNoSymbolicLinks(base, base, false);
        // Create one segment at a time so a pre-existing symlink cannot redirect writes.
        Path current = base;
        for (Path segment : base.relativize(directory)) {
            current = current.resolve(segment);
            if (Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current);
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Non-directory or link in TEST evidence namespace");
        }
        requireNoSymbolicLinks(base, directory, false);
        String redacted = (String) new E2EEvidenceCollector().sanitize(text);
        byte[] bytes = redacted.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Artifact length not permitted");
        Path file = directory.resolve(type + "-" + UUID.randomUUID() + ".json");
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return Map.of("path", base.relativize(file).toString().replace('\\', '/'),
                      "sha256", digest(bytes));
    }

    /** Fail closed for missing root, tampering, traversal, symlink or truncated/incomplete proof. */
    public static boolean verified(String configuredRoot, UUID workspaceId, UUID runId,
                                   String scenarioId, Map<String, Object> evidence) {
        if (configuredRoot == null || configuredRoot.isBlank() || evidence == null) return false;
        try {
            Object raw = evidence.get("proofArtifacts");
            if (!(raw instanceof Map<?, ?> proofs)) return false;
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            Path scope = namespace(root, workspaceId, runId, scenarioId);
            for (String kind : TYPES) {
                Object claimed = proofs.get(kind);
                if (!(claimed instanceof Map<?, ?> record)) return false;
                Object rawPath = record.get("path"), rawHash = record.get("sha256");
                if (!(rawPath instanceof String relative) || !(rawHash instanceof String expected) ||
                    !relative.matches("evidence/[0-9a-f-]{36}/[0-9a-f-]{36}/E2E-[0-9]{2}/[A-Za-z0-9_-]+\\.json") ||
                    !expected.matches("(?i)[0-9a-f]{64}")) return false;
                Path path = root.resolve(relative).normalize();
                if (!path.startsWith(scope) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
                requireNoSymbolicLinks(root, path, true);
                long size = Files.size(path);
                if (size < 1 || size > MAX_BYTES) return false;
                if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected.toLowerCase()),
                        HexFormat.of().parseHex(digest(Files.readAllBytes(path))))) return false;
            }
            return true;
        } catch (RuntimeException | IOException ex) {
            return false;
        }
    }
}
