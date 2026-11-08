package com.sakhtyar.analysis.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure, immutable, numeric-only Phase 05 TEST snapshot proof.
 * No repository I/O, no database or network writes, no PII support.
 * Production persistence/replay remain separate integration gates.
 */
public record Phase05NumericSnapshot(
        UUID workspaceId, UUID runId, UUID caseId,
        String currencyUnit, String priceVersion, String ruleVersion,
        String algorithmVersion, Instant createdAt,
        Map<String, BigDecimal> inputs, Map<String, BigDecimal> outputs,
        String inputHash, String outputHash, String envelopeHash) {

    public static final String SCHEMA_VERSION = "phase05-numeric-proof-v1";

    public Phase05NumericSnapshot {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!"IRR".equals(currencyUnit) && !"TOMAN".equals(currencyUnit))
            throw new IllegalArgumentException("Explicit IRR or TOMAN currency unit required.");
        version(priceVersion, "priceVersion");
        version(ruleVersion, "ruleVersion");
        version(algorithmVersion, "algorithmVersion");
        inputs = numericCopy(inputs, "inputs");
        outputs = numericCopy(outputs, "outputs");
        if (!hashValid(inputHash) || !hashValid(outputHash) || !hashValid(envelopeHash))
            throw new IllegalArgumentException("Snapshot requires SHA-256 hashes.");
    }

    public static Phase05NumericSnapshot freeze(UUID workspaceId, UUID runId, UUID caseId,
            String currencyUnit, String priceVersion, String ruleVersion,
            String algorithmVersion, Instant createdAt,
            Map<String,BigDecimal> inputs, Map<String,BigDecimal> outputs) {
        Map<String,BigDecimal> in = numericCopy(inputs, "inputs");
        Map<String,BigDecimal> out = numericCopy(outputs, "outputs");
        String iHash = sha256(encodeNumbers(in));
        String oHash = sha256(encodeNumbers(out));
        String envelope = sha256(encodeParts(SCHEMA_VERSION,
                Objects.requireNonNull(workspaceId, "workspaceId").toString(),
                Objects.requireNonNull(runId, "runId").toString(),
                Objects.requireNonNull(caseId, "caseId").toString(),
                currencyUnit, priceVersion, ruleVersion, algorithmVersion,
                Objects.requireNonNull(createdAt, "createdAt").toString(), iHash, oHash));
        return new Phase05NumericSnapshot(workspaceId,runId,caseId,currencyUnit,
                priceVersion,ruleVersion,algorithmVersion,createdAt,in,out,iHash,oHash,envelope);
    }

    /** Strongly compare all fields against an independently recomputed proof. */
    public boolean verify() {
        Phase05NumericSnapshot expected = freeze(workspaceId,runId,caseId,currencyUnit,
                priceVersion,ruleVersion,algorithmVersion,createdAt,inputs,outputs);
        return inputHash.equals(expected.inputHash)
                && outputHash.equals(expected.outputHash)
                && envelopeHash.equals(expected.envelopeHash);
    }

    private static Map<String,BigDecimal> numericCopy(Map<String,BigDecimal> src, String name) {
        if (src == null || src.isEmpty() || src.size() > 128)
            throw new IllegalArgumentException(name + " must contain 1..128 numeric fields.");
        TreeMap<String,BigDecimal> copy = new TreeMap<>();
        src.forEach((key,value) -> {
            if (key == null || !key.matches("[a-z][a-zA-Z0-9_]{0,63}") ||
                    key.toLowerCase(Locale.ROOT).matches(".*(password|token|secret|cookie|email|phone|nationalid|apikey).*" ) ||
                    value == null
                    || value.precision() > 38 || value.scale() > 12 || ((long)value.precision()-value.scale()) > 30)
                throw new IllegalArgumentException(name + " contains an invalid numeric field.");
            copy.put(key, value.stripTrailingZeros());
        });
        return Map.copyOf(copy);
    }

    private static void version(String value,String name) {
        if(value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Invalid " + name);
    }

    private static boolean hashValid(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String encodeNumbers(Map<String,BigDecimal> numbers) {
        StringBuilder result = new StringBuilder();
        new TreeMap<>(numbers).forEach((key,value) ->
                result.append(encodeParts(key,value.stripTrailingZeros().toPlainString())));
        return result.toString();
    }

    /** Length-prefix each field, preventing concatenation/delimiter ambiguities. */
    private static String encodeParts(String... parts) {
        StringBuilder result=new StringBuilder();
        for(String part: parts) {
            if(part==null) throw new IllegalArgumentException("Null snapshot metadata.");
            byte[] utf8=part.getBytes(StandardCharsets.UTF_8);
            result.append(utf8.length).append(':').append(part);
        }
        return result.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch(NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable.",ex);
        }
    }
}
