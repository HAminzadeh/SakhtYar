package com.sakhtyar.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** In-memory TEST configuration state machine. Does not activate platform config. */
public final class Phase06ConfigSandbox {
    public enum Status { DRAFT, REVIEWED, PUBLISHED, ROLLED_BACK }
    public record Snapshot(int version, Status status, String createdBy, String reviewedBy,
                           Map<String,String> values, String sha256) {}
    private final UUID workspaceId;
    private final UUID runId;
    private final Map<Integer,Snapshot> revisions = new LinkedHashMap<>();
    private int nextVersion=1;
    public Phase06ConfigSandbox(UUID workspaceId,UUID runId) {
        if(workspaceId==null || runId==null || workspaceId.equals(runId))throw new IllegalArgumentException("Unique TEST scope required");
        this.workspaceId=workspaceId;this.runId=runId;
    }
    public synchronized Snapshot draft(String actor,Map<String,String> values) {
        requireActor(actor);
        if(values==null || values.size()>50)throw new IllegalArgumentException("Bounded non-secret config required");
        TreeMap<String,String> safe=new TreeMap<>();
        for(var entry:values.entrySet()) {
            if(entry.getKey()==null || !entry.getKey().matches("test\\.[a-zA-Z0-9_.-]{1,60}") ||
               entry.getKey().toLowerCase().matches(".*(secret|token|password|credential|api.?key).*" ) ||
               entry.getValue()==null || entry.getValue().length()>256)
                throw new SecurityException("Only non-secret TEST keys supported");
            safe.put(entry.getKey(),entry.getValue());
        }
        int version=nextVersion++;
        Snapshot s=new Snapshot(version,Status.DRAFT,actor,null,Map.copyOf(safe),hash(safe));
        revisions.put(version,s);return s;
    }
    public synchronized Snapshot review(int version,String reviewer) {
        requireActor(reviewer);Snapshot s=require(version,Status.DRAFT);
        if(s.createdBy().equals(reviewer))throw new SecurityException("Separation of duties");
        return store(s,Status.REVIEWED,reviewer);
    }
    public synchronized Snapshot publishInSandboxOnly(int version,String publisher) {
        requireActor(publisher);Snapshot s=require(version,Status.REVIEWED);
        if(s.createdBy().equals(publisher))throw new SecurityException("Separation of duties");
        return store(s,Status.PUBLISHED,s.reviewedBy());
    }
    public synchronized Snapshot rollbackInSandboxOnly(int version,String actor) {
        requireActor(actor);Snapshot s=require(version,Status.PUBLISHED);
        if(s.createdBy().equals(actor))throw new SecurityException("Separation of duties");
        return store(s,Status.ROLLED_BACK,s.reviewedBy());
    }
    public synchronized Snapshot get(int version) {Snapshot s=revisions.get(version);if(s==null)throw new IllegalArgumentException("Unknown TEST revision");return s;}
    public UUID workspaceId(){return workspaceId;}
    public UUID runId(){return runId;}
    private Snapshot store(Snapshot s,Status status,String reviewer) {
        Snapshot next=new Snapshot(s.version(),status,s.createdBy(),reviewer,s.values(),s.sha256());
        revisions.put(s.version(),next);return next;
    }
    private Snapshot require(int version,Status status){Snapshot s=get(version);if(s.status()!=status)throw new IllegalStateException("Incorrect TEST revision state");return s;}
    private static void requireActor(String actor){if(actor==null||!actor.matches("[a-zA-Z][a-zA-Z0-9_-]{1,49}"))throw new SecurityException("TEST actor required");}
    private static String hash(Map<String,String> values) {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            for(var e:new TreeMap<>(values).entrySet()){
                md.update((e.getKey().length()+":"+e.getKey()+e.getValue().length()+":"+e.getValue()+";").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
