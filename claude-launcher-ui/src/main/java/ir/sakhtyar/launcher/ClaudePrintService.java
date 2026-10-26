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
        List<String> cmd = new ArrayList<>(List.of("cmd.exe", "/d", "/c", "ccr.cmd", profile, "cli", "--", "-p", "--output-format", "json", "--max-turns", toolsEnabled ? "3" : "1"));
        // Plain chat must not inherit the 33k-token coding-tool surface.
        // --permission-mode plan DOES NOT disable tools; it actually retains read/plan tools.
        if (!toolsEnabled) {
            cmd.addAll(List.of("--tools", "", "--disallowedTools", "mcp__*", "--disable-slash-commands"));
        } else {
            // Project mode can read the repository and call configured MCPs, never write source.
            cmd.addAll(List.of("--tools", "Read,Glob,Grep", "--permission-mode", "plan"));
        }
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
            boolean errorResult=false;
            try {
                JsonNode doc=JSON.readTree(payload);
                if (doc.isObject()) {
                    String reply=doc.path("result").asText("");
                    if (reply.isBlank()) reply=doc.path("message").asText("");
                    String id=doc.path("session_id").asText("");
                    errorResult=doc.path("is_error").asBoolean(false);
                    if (!reply.isBlank() && p.exitValue()==0 && !errorResult) return new Reply(reply,id.isBlank()?sessionId:id,p.exitValue());
                }
            } catch(Exception ignored) { }
            if (p.exitValue()!=0 || errorResult)
                throw new IOException(explainFailure(payload, p.exitValue()));
            return new Reply(scrub(payload), sessionId, p.exitValue());
        } finally {running=null;}
    }
    /** Redact verbose router errors before exposing them to a chat bubble. */
    static String explainFailure(String payload, int exitCode) {
        String lower=payload.toLowerCase(Locale.ROOT);
        if (lower.contains("not configured for target provider") || lower.contains("model_resolution"))
            return "CCR model routing failed. The configured fallback model is missing from its target provider. "
                + "Open CCR > Global Routing and Agent Profiles; remove or repair the invalid fallback. "
                + "Test the profile using CCR's own provider check. (exit " + exitCode + ")";
        if (lower.contains("all target providers failed") || lower.contains("ccr_fallback"))
            return "CCR provider/fallback request failed. Check CCR Logs and the selected profile's global and rule-level fallback settings. (exit " + exitCode + ")";
        if (lower.contains("401") || lower.contains("unauthorized"))
            return "Provider authentication failed (401). Check credentials in CCR; do not share API keys.";
        if (lower.contains("429"))return "Provider rate limit (429). Wait or select a configured alternative provider.";
        if (lower.contains("not recognized") && lower.contains("tools"))
            return "Claude CLI rejected tool-restriction flags; check Claude Code version and --help.";
        String tail=scrub(payload);
        return "Claude/CCR request failed (exit " + exitCode + "): " +
            (tail.length()>850?tail.substring(0,850)+"...":tail);
    }
    private static String scrub(String s) {
        String clipped=s.length()>12000?s.substring(0,12000)+"\n[truncated]":s;
        return clipped.replaceAll("(?i)(api[_-]?key|auth[_-]?token|password|secret)([=: ]+)[^\\s,;]+","$1$2[REDACTED]")
            .replaceAll("sk-[A-Za-z0-9_-]{15,}","[REDACTED]");
    }
}
