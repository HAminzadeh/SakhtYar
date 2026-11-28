package com.sakhtyar.material.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Versioned policy for market references; the original quote is never overwritten. */
public final class MaterialPricePolicy {
    private MaterialPricePolicy() {}
    public static final String VERSION = "2.0";
    public static final Duration MAX_AGE = Duration.ofDays(30);
    public static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.50");
    public static boolean fresh(Instant observed, Instant now) {
        return observed != null && !observed.isAfter(now) && observed.plus(MAX_AGE).isAfter(now);
    }
    public static Instant observationTime(Instant observed, Instant now) {
        if (observed == null || observed.isAfter(now) || observed.isBefore(Instant.parse("1900-01-01T00:00:00Z")))
            throw new IllegalArgumentException("Invalid price observation timestamp.");
        return observed.truncatedTo(ChronoUnit.MICROS);
    }
    public static boolean confident(BigDecimal value) {
        return value != null && value.compareTo(MIN_CONFIDENCE) >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }
    public static String region(String value) {
        String s = MaterialFoundation.optional(value,160);
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
    public static String regionValue(String value) {
        String s = region(value); return s.isEmpty() ? null : s;
    }
    public static String sourceUrl(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) throw new IllegalArgumentException("External price evidence URL required.");
            return null;
        }
        String s = raw.strip();
        if(s.length()>3000 || s.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid price source URL.");
        URI u = URI.create(s);
        if (!("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) || u.getHost()==null || u.getUserInfo()!=null)
            throw new IllegalArgumentException("Invalid price source URL.");
        return s; // evidence only; never fetch it
    }
    public static String currencyGroup(String code) {
        String c = MaterialFoundation.currencyCode(code);
        return "TOMAN".equals(c) ? "IRR" : c;
    }
    public static BigDecimal currencyAmount(BigDecimal value,String from,String to) {
        String a=MaterialFoundation.currencyCode(from), b=MaterialFoundation.currencyCode(to);
        if(a.equals(b)) return value;
        if(a.equals("TOMAN") && b.equals("IRR")) return value.multiply(BigDecimal.TEN);
        if(a.equals("IRR") && b.equals("TOMAN")) return value.divide(BigDecimal.TEN);
        throw new IllegalArgumentException("No verified FX conversion for requested currencies.");
    }
    public static BigDecimal unitPrice(BigDecimal amount,BigDecimal quantity,String fromUnit,String baseUnit,String fromCurrency,String toCurrency) {
        BigDecimal q=MaterialFoundation.quantity(quantity);
        // Legacy unknown unit codes may be retained in catalog but cannot be published as prices.
        BigDecimal baseQuantity=MaterialFoundation.convert(q,fromUnit,baseUnit);
        BigDecimal value=currencyAmount(MaterialFoundation.amount(amount),fromCurrency,toCurrency).divide(baseQuantity,MathContext.DECIMAL128);
        // Validate the persisted two-decimal representation; do not silently publish zero or overflow.
        MaterialFoundation.amount(value.setScale(2,RoundingMode.HALF_UP));
        return value;
    }
    /** Exact median for anomaly comparisons; presentation rounding must not move thresholds. */
    public static BigDecimal exactMedian(List<BigDecimal> values) {
        if(values==null || values.isEmpty() || values.stream().anyMatch(v->v==null || v.signum()<0))
            throw new IllegalArgumentException("Invalid median input.");
        List<BigDecimal> sorted=values.stream().sorted().toList();
        int middle=sorted.size()/2;
        return sorted.size()%2==0?sorted.get(middle-1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2)):sorted.get(middle);
    }
    public static String digest(String... fields) {
        try {
            MessageDigest d=MessageDigest.getInstance("SHA-256");
            for(String field:fields){
                if(field==null){d.update((byte)0);continue;}
                byte[] bytes=field.getBytes(StandardCharsets.UTF_8);
                d.update((byte)1);d.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());d.update(bytes);
            }
            return HexFormat.of().formatHex(d.digest());
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
