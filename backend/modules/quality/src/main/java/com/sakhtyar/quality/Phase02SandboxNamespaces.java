package com.sakhtyar.quality;

import java.util.UUID;

/** Run-owned namespacing for future TEST storage adapters; does not delete any objects. */
public final class Phase02SandboxNamespaces {
    private final String minioPrefix;
    private final String redisPrefix;

    public Phase02SandboxNamespaces(UUID workspace, UUID run) {
        if(workspace==null || run==null) throw new IllegalArgumentException("Workspace and run required");
        minioPrefix="test/"+workspace+"/"+run+"/";
        redisPrefix="sakhtyar:test:"+workspace+":"+run+":";
    }
    public String minioPrefix() { return minioPrefix; }
    public String redisPrefix() { return redisPrefix; }
    public String minioKey(String logicalName) {
        return minioPrefix+validated(logicalName);
    }
    public String redisKey(String logicalName) { return redisPrefix+validated(logicalName); }
    public boolean ownsMinioKey(String key) {
        return key!=null && key.startsWith(minioPrefix) && isSafeSuffix(key.substring(minioPrefix.length()));
    }
    public boolean ownsRedisKey(String key) {
        return key!=null && key.startsWith(redisPrefix) && isSafeSuffix(key.substring(redisPrefix.length()));
    }
    private static String validated(String logicalName) {
        if(!isSafeSuffix(logicalName)) throw new IllegalArgumentException("Unsafe TEST storage key");
        return logicalName;
    }
    private static boolean isSafeSuffix(String s) {
        return s!=null && s.matches("[A-Za-z0-9][A-Za-z0-9_./-]{0,180}") &&
               !s.contains("..") && !s.contains("//") && !s.endsWith("/");
    }
}
