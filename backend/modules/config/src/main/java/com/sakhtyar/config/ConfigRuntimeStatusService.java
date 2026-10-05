package com.sakhtyar.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ConfigRuntimeStatusService {
    private final ConfigRegistry registry;
    private final Environment env;

    public ConfigRuntimeStatusService(ConfigRegistry registry, Environment env) {
        this.registry = registry;
        this.env = env;
    }

    public List<Map<String, Object>> status() {
        Properties pending = readPending();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConfigDefinition d : registry.all()) {
            String property = d.propertyName();
            boolean hasPending = property != null && pending.containsKey(property);
            boolean differs = false;
            if (hasPending) {
                String running = env.getProperty(property);
                differs = !Objects.equals(running, pending.getProperty(property));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", d.key());
            row.put("pendingOverride", hasPending);
            row.put("pendingRestart", hasPending && differs);
            row.put("state", hasPending && differs ? "PENDING_RESTART" : "APPLIED");
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> test(String key) {
        ConfigDefinition d = registry.find(key)
            .orElseThrow(() -> new IllegalArgumentException("Unknown config: " + key));
        String raw = d.propertyName() == null ? d.defaultValue() : env.getProperty(d.propertyName(), d.defaultValue());
        if (raw == null || raw.isBlank()) {
            return result(key, false, 0L, "No effective value is configured.");
        }

        long started = System.nanoTime();
        try {
            Target target = targetFor(d, raw);
            if (target == null) {
                return result(key, false, elapsed(started), "This setting does not support connectivity testing.");
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target.host(), target.port()), 2500);
            }
            return result(key, true, elapsed(started), "Connection successful to " + target.host() + ":" + target.port());
        } catch (Exception e) {
            return result(key, false, elapsed(started), e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), "Connection failed"));
        }
    }

    private Target targetFor(ConfigDefinition d, String raw) {
        String key = d.key();
        if ("database.url".equals(key)) {
            String x = raw.replaceFirst("^jdbc:", "");
            URI u = URI.create(x);
            return new Target(u.getHost(), u.getPort() > 0 ? u.getPort() : 5432);
        }
        if ("redis.host".equals(key)) {
            int port = Integer.parseInt(env.getProperty("spring.data.redis.port", "6379"));
            return new Target(raw, port);
        }
        if (key.endsWith("baseUrl") || key.endsWith(".endpoint") || key.startsWith("operations.") || "ai.ollama.baseUrl".equals(key)) {
            URI u = URI.create(raw);
            int port = u.getPort();
            if (port < 0) port = "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
            return new Target(u.getHost(), port);
        }
        return null;
    }

    private Properties readPending() {
        Properties p = new Properties();
        Path f = Paths.get(".local", "config-secrets.properties").toAbsolutePath().normalize();
        if (!Files.exists(f)) return p;
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
            return p;
        } catch (Exception e) {
            return p;
        }
    }

    private long elapsed(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private Map<String, Object> result(String key, boolean ok, long latencyMs, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", key);
        r.put("ok", ok);
        r.put("latencyMs", latencyMs);
        r.put("message", message);
        return r;
    }

    private record Target(String host, int port) {}
}