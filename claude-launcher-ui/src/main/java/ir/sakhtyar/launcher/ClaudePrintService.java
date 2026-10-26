package ir.sakhtyar.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Invokes the currently configured CCR Claude profile without bypassing its tools or local MCPs. */
public final class ClaudePrintService {
    public record Reply(String content, String sessionId, int exitCode) { }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;
    private volatile Process running;
    public ClaudePrintService(Path root) { this.root = root; }
    public void cancel() { Process p = running; if (p != null && p.isAlive()) p.destroyForcibly(); }
    public Reply ask(String profile, String text, String sessionId, boolean toolsEnabled) throws Exception {
        if (!LauncherCore.validProfile(profile)) throw new IllegalArgumentException("Invalid CCR profile");
        if (text == null || text.isBlank() || text.length() > 100000) throw new IllegalArgumentException("Invalid prompt size");
        List<String> cmd = new ArrayList<>(List.of("cmd.exe", "/d", "/c", "ccr.cmd", profile, "cli", "--", "-p", "--output-format", "json", "--max-turns", "3"));
        if (!toolsEnabled) cmd.addAll(List.of("--permission-mode", "plan"));
        if (sessionId != null && !sessionId.isBlank()) cmd.addAll(List.of("--resume", sessionId));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true);
        Map<String,String> env = pb.environment();
        int port = LauncherCore.port("SAKHTYAR_PROXY_PORT",10808);
        env.put("HTTP_PROXY", "http://127.0.0.1:"+port);
        env.put("HTTPS_PROXY", "http://127.0.0.1:"+port);
        Map<String,Object> settings = new LauncherCore(root).readSettings();
        if (!Boolean.FALSE.equals(settings.get("shortToolDescriptions"))) env.put("CLAUDE_CODE_SIMPLE_SYSTEM_PROMPT", "1");
        if (Boolean.TRUE.equals(settings.get("toolSearchEnabled"))) env.put("ENABLE_TOOL_SEARCH", "auto:5");
        String graph = String.valueOf(settings.getOrDefault("codeGraphProfile","core"));
        if (Set.of("core","graph","memory","all").contains(graph)) env.put("CODEGRAPH_TOOL_PROFILE",graph);
        pb.environment().put("PYTHONIOENCODING","utf-8");
        Process p = pb.start(); running = p;
        try {
            try (OutputStream out = p.getOutputStream()) { out.write(text.getBytes(StandardCharsets.UTF_8)); out.write('\n'); }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            Thread drain = new Thread(() -> {
                try (InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n=in.read(buf))!=-1) {
                        synchronized (captured) {
                            if (captured.size()<3_000_000) captured.write(buf,0,Math.min(n,3_000_000-captured.size()));
                        }
                    }
                } catch(IOException ignored) { }
            },"claude-chat-output");drain.setDaemon(true);drain.start();
            if (!p.waitFor(180, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("CCR request timed out after 180 seconds"); }
            drain.join(2000);
            String result;
            synchronized (captured) { result = captured.toString(StandardCharsets.UTF_8); }
            String payload = result.strip();
            try {
                JsonNode doc=JSON.readTree(payload);
                if (doc.isObject()) {
                    String reply=doc.path("result").asText("");
                    if (reply.isBlank()) reply=doc.path("message").asText("");
                    String id=doc.path("session_id").asText("");
                    if (!reply.isBlank()) return new Reply(reply,id.isBlank()?sessionId:id,p.exitValue());
                }
            } catch(Exception ignored) { }
            if (p.exitValue()!=0) throw new IOException("CCR returned exit code " + p.exitValue() + ": " + scrub(payload));
            return new Reply(scrub(payload), sessionId, p.exitValue());
        } finally {running=null;}
    }
    private static String scrub(String s) {
        String clipped=s.length()>12000?s.substring(0,12000)+"\n[truncated]":s;
        return clipped.replaceAll("(?i)(api[_-]?key|auth[_-]?token|password|secret)([=: ]+)[^\\s,;]+","$1$2[REDACTED]")
            .replaceAll("sk-[A-Za-z0-9_-]{15,}","[REDACTED]");
    }
}
