package com.sakhtyar.quality;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Deny-by-default sandbox policy. Not wired into production adapters; no global claim is made. */
public final class Phase02OutboundGuard {
    public enum Effect { EMAIL, SMS, WEBHOOK, CRAWLER, AI, PROVIDER, MINIO, REDIS }
    private static final Set<Integer> MOCK_PORTS=Set.of(1080,8089,9099,18080);
    private Phase02OutboundGuard() {}

    public static void requireLocalMockEndpoint(Effect effect, URI endpoint, boolean explicitTestMode) {
        if(!explicitTestMode || effect==null || endpoint==null || endpoint.getUserInfo()!=null ||
            endpoint.getRawFragment()!=null || endpoint.getRawQuery()!=null ||
            !"http".equalsIgnoreCase(endpoint.getScheme()) ||
            !"127.0.0.1".equals(endpoint.getHost()) ||
            !MOCK_PORTS.contains(endpoint.getPort()))
            throw new SecurityException("Phase02 outbound blocked; localhost mock requires explicit test mode");
    }
    public static void denyActualOutbound(Effect effect) {
        throw new SecurityException("Phase02 TEST network egress is denied: "+effect);
    }
    /** Evidence-only sink: counts effects but stores no messages, PII, tokens, or destinations. */
    public static final class FakeSink {
        private final EnumMap<Effect,AtomicInteger> counters=new EnumMap<>(Effect.class);
        public FakeSink() { for (Effect e:Effect.values()) counters.put(e,new AtomicInteger()); }
        public void capture(Effect effect) {
            if(effect==null) throw new IllegalArgumentException("Effect required");
            counters.get(effect).incrementAndGet();
        }
        public int count(Effect effect) { return counters.get(effect).get(); }
        public Map<String,Integer> counts() {
            Map<String,Integer> result=new java.util.TreeMap<>();
            for(Effect e:Effect.values()) result.put(e.name(),count(e));
            return Map.copyOf(result);
        }
    }
}
