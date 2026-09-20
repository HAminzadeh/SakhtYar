package com.sakhtyar.agents.provider.ollama;

import com.sakhtyar.agents.provider.AiProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Owns the local Ollama server lifecycle for a directly-run SakhtYar backend.
 *
 * If Ollama is already listening, SakhtYar reuses it. Otherwise it starts
 * `ollama serve`, applies local performance tuning and optionally preloads the
 * configured model so the first user request does not pay the model-load cost.
 */
@Component
public class OllamaProcessManager implements SmartLifecycle {

    private static final System.Logger LOG =
            System.getLogger(OllamaProcessManager.class.getName());

    private final AiProperties aiProperties;
    private final OllamaProperties properties;
    private final OllamaPerformanceTuner performanceTuner;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile Process process;
    private volatile boolean running;
    private volatile boolean ownsProcess;

    public OllamaProcessManager(
            AiProperties aiProperties,
            OllamaProperties properties,
            OllamaPerformanceTuner performanceTuner,
            ObjectMapper objectMapper
    ) {
        this.aiProperties = aiProperties;
        this.properties = properties;
        this.performanceTuner = performanceTuner;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public synchronized void start() {
        if (!shouldManageOllama()) {
            running = false;
            return;
        }

        if (apiReachable()) {
            running = true;
            ownsProcess = false;
            LOG.log(System.Logger.Level.INFO,
                    "Ollama is already available at {0}; reusing existing process.",
                    properties.getBaseUrl());
            preloadIfEnabled();
            return;
        }

        String executable = resolveExecutable();
        ProcessBuilder builder = new ProcessBuilder(executable, "serve");
        builder.redirectErrorStream(true);
        configureEnvironment(builder.environment());

        try {
            LOG.log(System.Logger.Level.INFO,
                    "Starting local Ollama using: {0}", executable);
            process = builder.start();
            ownsProcess = true;
            startLogPump(process);

            if (!waitForApi()) {
                int exitCode = process.isAlive() ? Integer.MIN_VALUE : process.exitValue();
                stopOwnedProcess();
                running = false;
                throw new IllegalStateException(
                        "Ollama did not become ready within "
                                + properties.getStartupTimeoutSeconds()
                                + " seconds. Exit code: "
                                + (exitCode == Integer.MIN_VALUE ? "still-running" : exitCode)
                );
            }

            running = true;
            LOG.log(System.Logger.Level.INFO,
                    "Local Ollama is ready at {0} (model: {1}).",
                    properties.getBaseUrl(),
                    properties.getModel());

            preloadIfEnabled();
        } catch (IOException ex) {
            running = false;
            ownsProcess = false;
            process = null;
            throw new IllegalStateException(
                    "Could not start Ollama. Configure app.ai.ollama.executable "
                            + "or install Ollama in a standard Windows location.",
                    ex
            );
        }
    }

    @Override
    public synchronized void stop() {
        if (ownsProcess && properties.isStopOnShutdown()) {
            stopOwnedProcess();
        }
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running && (apiReachable() || (process != null && process.isAlive()));
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE + 100;
    }

    public boolean ownsProcess() {
        return ownsProcess;
    }

    public boolean apiReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(tagsUri())
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private boolean shouldManageOllama() {
        if (!aiProperties.isEnabled() || !properties.isAutoStart()) {
            return false;
        }
        String provider = aiProperties.getProvider();
        return provider != null && provider.trim().equalsIgnoreCase("ollama");
    }

    private boolean waitForApi() {
        int timeoutSeconds = Math.max(5, properties.getStartupTimeoutSeconds());
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);

        while (Instant.now().isBefore(deadline)) {
            if (apiReachable()) {
                return true;
            }
            Process current = process;
            if (current != null && !current.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(400L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return apiReachable();
    }

    private void configureEnvironment(Map<String, String> env) {
        if (properties.isForceCpu()) {
            env.put("CUDA_VISIBLE_DEVICES", "-1");
            String library = properties.getLlmLibrary();
            if (library != null && !library.isBlank()) {
                env.put("OLLAMA_LLM_LIBRARY", library.trim());
            }
        }
        if (properties.isNoCloud()) {
            env.put("OLLAMA_NO_CLOUD", "true");
        }

        env.put("OLLAMA_KEEP_ALIVE", normalizedKeepAlive());
        env.put("OLLAMA_NUM_PARALLEL", String.valueOf(Math.max(1, properties.getNumParallel())));
        env.put("OLLAMA_MAX_LOADED_MODELS",
                String.valueOf(Math.max(1, properties.getMaxLoadedModels())));
        env.put("OLLAMA_CONTEXT_LENGTH",
                String.valueOf(Math.max(512, properties.getContextLength())));

        URI uri = safeBaseUri();
        if (uri != null && uri.getHost() != null) {
            int port = uri.getPort() > 0 ? uri.getPort() : 11434;
            env.put("OLLAMA_HOST", uri.getHost() + ":" + port);
        }
    }

    private void preloadIfEnabled() {
        if (!properties.isPreloadModel()) {
            return;
        }

        Instant startedAt = Instant.now();
        try {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("prompt", "");
            payload.put("stream", false);
            payload.put("keep_alive", keepAliveValue());
            payload.put("options", preloadOptions());

            HttpRequest request = HttpRequest.newBuilder(apiUri("/api/generate"))
                    .timeout(Duration.ofSeconds(
                            Math.max(10, properties.getPreloadTimeoutSeconds())))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload),
                            StandardCharsets.UTF_8
                    ))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                long millis = Duration.between(startedAt, Instant.now()).toMillis();
                LOG.log(System.Logger.Level.INFO,
                        "Ollama model preloaded in {0}ms: model={1}, ctx={2}, threads={3}",
                        millis,
                        properties.getModel(),
                        properties.getContextLength(),
                        performanceTuner.numThreads());
            } else {
                LOG.log(System.Logger.Level.WARNING,
                        "Ollama preload returned HTTP {0}: {1}",
                        response.statusCode(),
                        truncate(response.body(), 600));
            }
        } catch (IOException | JacksonException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "Ollama model preload failed: {0}", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING,
                    "Ollama model preload was interrupted.");
        }
    }

    private Map<String, Object> preloadOptions() {
        LinkedHashMap<String, Object> options = new LinkedHashMap<>();
        options.put("num_ctx", Math.max(512, properties.getContextLength()));
        options.put("num_batch", Math.max(1, properties.getNumBatch()));
        options.put("num_thread", Math.max(1, performanceTuner.numThreads()));
        options.put("use_mmap", properties.isUseMmap());
        return options;
    }

    private Object keepAliveValue() {
        String value = normalizedKeepAlive();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private String normalizedKeepAlive() {
        String value = properties.getKeepAlive();
        return value == null || value.isBlank() ? "-1" : value.trim();
    }

    private String resolveExecutable() {
        String configured = properties.getExecutable();
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.trim());
            if (Files.isRegularFile(path)) {
                return path.toString();
            }
            if (path.getNameCount() == 1) {
                return configured.trim();
            }
            throw new IllegalStateException(
                    "Configured Ollama executable does not exist: " + configured
            );
        }

        List<Path> candidates = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(Path.of(localAppData, "Programs", "Ollama", "ollama.exe"));
            candidates.add(Path.of(localAppData, "Ollama", "ollama.exe"));
        }

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            candidates.add(Path.of(programFiles, "Ollama", "ollama.exe"));
        }

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }

        return isWindows() ? "ollama.exe" : "ollama";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private URI tagsUri() {
        return apiUri("/api/tags");
    }

    private URI apiUri(String path) {
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:11434";
        }
        String normalized = base.endsWith("/")
                ? base.substring(0, base.length() - 1)
                : base;
        return URI.create(normalized + path);
    }

    private URI safeBaseUri() {
        try {
            return URI.create(properties.getBaseUrl());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void startLogPump(Process target) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(target.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.log(System.Logger.Level.DEBUG, "[ollama] {0}", line);
                }
            } catch (IOException ex) {
                if (target.isAlive()) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Could not read Ollama process output: {0}", ex.getMessage());
                }
            }
        }, "sakhtyar-ollama-log");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopOwnedProcess() {
        Process current = process;
        if (current == null) {
            ownsProcess = false;
            return;
        }

        try {
            current.descendants().forEach(ProcessHandle::destroy);
            current.destroy();
            if (!current.waitFor(3, TimeUnit.SECONDS)) {
                current.descendants().forEach(ProcessHandle::destroyForcibly);
                current.destroyForcibly();
                current.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        } finally {
            process = null;
            ownsProcess = false;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength) + "...";
    }
}
