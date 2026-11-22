package com.sakhtyar.knowledge.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Deterministic, SOURCE_ONLY OCR contract. Never infer that a real PDF was OCR'ed. */
public final class Phase04FakeOcrEngine {
    private Phase04FakeOcrEngine() {}
    public record OcrPageArtifact(int page, String sourceSha256, String pageSha256,
                                  String textSha256, String text, String engine,
                                  String provenance) {}

    public static OcrPageArtifact transcribe(byte[] syntheticPage, int page,
                                             String sourceSha256, String suppliedText) {
        Objects.requireNonNull(syntheticPage);
        if (page < 1 || suppliedText == null || suppliedText.isBlank() ||
            sourceSha256 == null || !sourceSha256.matches("(?i)[a-f0-9]{64}"))
            throw new SecurityException("Synthetic page provenance missing");
        return new OcrPageArtifact(page, sourceSha256.toLowerCase(), hash(syntheticPage),
                hash(suppliedText.getBytes(StandardCharsets.UTF_8)), suppliedText,
                "FAKE_DETERMINISTIC", "SYNTHETIC_ONLY_NOT_LEGAL_EVIDENCE");
    }

    public static double characterErrorRate(String expected, String observed) {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(observed);
        int[] a = expected.codePoints().toArray();
        int[] b = observed.codePoints().toArray();
        if (a.length == 0) return b.length == 0 ? 0 : 1;
        int[] prev = new int[b.length + 1], next = new int[b.length + 1];
        for (int j = 0; j <= b.length; j++) prev[j] = j;
        for (int i = 1; i <= a.length; i++) {
            next[0] = i;
            for (int j = 1; j <= b.length; j++)
                next[j] = Math.min(Math.min(prev[j] + 1, next[j - 1] + 1),
                    prev[j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1));
            int[] tmp = prev; prev = next; next = tmp;
        }
        return (double) prev[b.length] / a.length;
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
