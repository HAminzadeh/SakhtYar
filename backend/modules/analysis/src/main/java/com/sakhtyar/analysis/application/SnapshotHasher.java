package com.sakhtyar.analysis.application;

import java.security.MessageDigest;
import java.util.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

public final class SnapshotHasher {
    private final ObjectMapper mapper;

    public SnapshotHasher(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public String sha256(Map<String,Object> input,Map<String,Object> result,Map<String,Object> metadata) {
        Map<String,Object> envelope=new TreeMap<>();
        envelope.put("input",input==null?Map.of():input);
        envelope.put("metadata",metadata==null?Map.of():metadata);
        envelope.put("result",result==null?Map.of():result);

        try {
            byte[] canonical=mapper
                    .writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(envelope);

            byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Snapshot payload cannot be serialized.",e);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable.",e);
        }
    }
}