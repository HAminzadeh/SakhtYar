package com.sakhtyar.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** No JDBC, network sender, address, phone, email or production adapter. */
public final class Phase06FakeNotificationOutbox {
    public record Entry(UUID workspaceId, UUID runId, String key, String channel,
                        String status, int attempts, Instant updatedAt) {}
    private final Clock clock;
    private final Duration cooldown;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public Phase06FakeNotificationOutbox(Clock clock, Duration cooldown) {
        if (clock == null || cooldown == null || cooldown.isNegative()) throw new IllegalArgumentException("clock/cooldown required");
        this.clock=clock; this.cooldown=cooldown;
    }
    private String id(UUID workspace, UUID run, String key) {
        if(workspace==null || run==null || workspace.equals(run) || key==null || !key.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid TEST outbox scope");
        return workspace+"/"+run+"/"+key;
    }
    public synchronized Entry enqueue(UUID workspace,UUID run,String key,String channel) {
        String id=id(workspace,run,key);
        if(channel==null || !channel.matches("FAKE_(EMAIL|SMS|WEBHOOK)")) throw new SecurityException("Only fake TEST channels allowed");
        return entries.computeIfAbsent(id,ignored->new Entry(workspace,run,key,channel,"PENDING",0,clock.instant()));
    }
    public synchronized Entry attempt(UUID workspace,UUID run,String key,boolean injectedFailure) {
        String id=id(workspace,run,key);
        Entry previous=entries.get(id);
        if(previous==null)throw new IllegalArgumentException("TEST entry not found");
        if("SENT".equals(previous.status())) return previous;
        if(previous.attempts()>0 && previous.updatedAt().plus(cooldown).isAfter(clock.instant())) return previous;
        String status=injectedFailure?"FAILED":"SENT";
        Entry next=new Entry(workspace,run,key,previous.channel(),status,previous.attempts()+1,clock.instant());
        entries.put(id,next);
        return next;
    }
    public synchronized int teardown(UUID workspace, UUID run) {
        id(workspace,run,"root");
        int before=entries.size();
        entries.entrySet().removeIf(e->e.getValue().workspaceId().equals(workspace)&&e.getValue().runId().equals(run));
        return before-entries.size();
    }
    public synchronized int size(){return entries.size();}
}
