package com.sakhtyar.quality;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Deterministic, namespaced identifiers. No external side effects. */
public final class Phase02FixtureKeys {
    private Phase02FixtureKeys() {}

    public static UUID fixtureId(UUID workspace, UUID run, String scenario, String logicalName) {
        if(workspace==null || run==null || scenario==null || logicalName==null ||
            !scenario.matches("E2E-(0[1-9]|1[0-9]|2[0-4])") ||
            !logicalName.matches("[a-z][a-z0-9_-]{0,59}"))
            throw new IllegalArgumentException("Invalid fixture identifiers");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] bytes=digest.digest(("SakhtYar/fixture-v1/"+workspace+"/"+run+"/"+scenario+"/"+logicalName)
                .getBytes(StandardCharsets.UTF_8));
            bytes[6]=(byte)((bytes[6]&0x0f)|0x50);
            bytes[8]=(byte)((bytes[8]&0x3f)|0x80);
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(bytes);
            return new UUID(b.getLong(),b.getLong());
        } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable",ex); }
    }

    public static String requestKey(UUID workspace, UUID run, String scenario) {
        return "p02_"+fixtureId(workspace,run,scenario,"root").toString().replace("-","");
    }
}
