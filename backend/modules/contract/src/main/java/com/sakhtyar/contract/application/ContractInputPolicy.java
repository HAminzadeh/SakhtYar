package com.sakhtyar.contract.application;

import static com.sakhtyar.contract.api.ContractDtos.UpsertContractRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** No signature, approval, acceptance or legal consent may be asserted by untrusted input. */
public final class ContractInputPolicy {
    private static final Set<String> RESERVED_METADATA = Set.of(
            "signed", "signedat", "signedby", "signature", "signatures", "esignature",
            "esignatures", "approved", "approvedat", "approvedby", "consent", "consents",
            "verifiedsignature", "legallybinding", "legalapproval");
    private static final int MAX_ENTRIES = 300;
    private ContractInputPolicy() { }

    public static void validateDraft(UpsertContractRequest r) {
        if (r == null || blank(r.contractCode()) || blank(r.contractType()) || blank(r.status()) || blank(r.title()) ||
                blank(r.schemaVersion())) bad("Contract fields are required.");
        if (r.contractCode().trim().length()>80 || r.contractType().trim().length()>60 ||
                r.title().trim().length()>300 || r.schemaVersion().trim().length()>20) bad("Contract field too long.");
        if (!r.contractCode().trim().matches("[\\p{L}\\p{N}_.-]{1,80}")) bad("Invalid contract code.");
        if (!"DRAFT".equalsIgnoreCase(r.status().trim()) || r.signedAt()!=null)
            bad("Unsigned drafts only: approval and signing require a separately verified legal workflow.");
        if (r.effectiveFrom()!=null && r.effectiveTo()!=null && r.effectiveTo().isBefore(r.effectiveFrom()))
            bad("effectiveTo cannot be before effectiveFrom.");
        validateObject(r.terms(), "terms");
        validateObject(r.metadata(), "metadata");
        if (r.metadata()!=null) checkReservedMetadata(r.metadata());
    }

    private static void checkReservedMetadata(Object value) {
        if (value instanceof Map<?,?> map) {
            for (var entry:map.entrySet()) {
                String name=((String)entry.getKey()).replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (RESERVED_METADATA.contains(name))
                    bad("Unverified signature or legal approval metadata is prohibited.");
                checkReservedMetadata(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            for (Object item:list) checkReservedMetadata(item);
        }
    }

    private static boolean blank(String s) { return s==null || s.isBlank(); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }

    public static void validateObject(Map<String,Object> value,String label) {
        if (value==null) return;
        int[] count = {0};
        validateNode(value, 0, count);
    }
    private static void validateNode(Object value,int depth,int[] count) {
        if (value==null) bad("Null values in contract JSON are not supported.");
        if (depth>6 || ++count[0]>MAX_ENTRIES) bad("Contract JSON exceeds safe limits.");
        if (value instanceof Map<?,?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || blank(key) || key.length()>120 ||
                        key.chars().anyMatch(Character::isISOControl)) bad("Invalid contract JSON key.");
                validateNode(entry.getValue(),depth+1,count);
            }
        } else if (value instanceof List<?> list) {
            for (Object item:list) validateNode(item,depth+1,count);
        } else if (value instanceof String s) {
            if (s.length()>20000) bad("Contract JSON text too long.");
        } else if (value instanceof Double d) {
            if (!Double.isFinite(d)) bad("Non-finite contract number.");
        } else if (value instanceof Float f) {
            if (!Float.isFinite(f)) bad("Non-finite contract number.");
        } else if (value!=null && !(value instanceof Number) && !(value instanceof Boolean)) {
            bad("Unsupported contract JSON value.");
        }
    }
}
