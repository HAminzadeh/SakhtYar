package com.sakhtyar.knowledge.preparation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class PythonKnowledgePreparationProcess {
    private final Path python;
    private final Path engine;
    private final Duration timeout;

    public PythonKnowledgePreparationProcess(
        @Value("${app.knowledge.preparation.python:.local/venv-knowledge-preparation/Scripts/python.exe}") String python,
        @Value("${app.knowledge.preparation.engine:tools/knowledge-preparation/pipeline.py}") String engine,
        @Value("${app.knowledge.preparation.timeout-minutes:120}") long timeoutMinutes) {
        this.python = Path.of(python).toAbsolutePath().normalize();
        this.engine = Path.of(engine).toAbsolutePath().normalize();
        this.timeout = Duration.ofMinutes(timeoutMinutes);
    }

    public KnowledgePreparationResult run(KnowledgePreparationCommand cmd) {
        requireFile(python, "Python executable");
        requireFile(engine, "Knowledge pipeline");
        Path input = cmd.inputRoot().toAbsolutePath().normalize();
        Path output = cmd.outputRoot().toAbsolutePath().normalize();
        if (!Files.exists(input)) {
            throw new IllegalArgumentException("Knowledge input does not exist: " + input);
        }

        List<String> args = List.of(
            python.toString(), engine.toString(),
            "--run-id", cmd.runId().toString(),
            "--workflow-id", cmd.workflowId().toString(),
            "--correlation-id", cmd.correlationId().toString(),
            "--input-root", input.toString(),
            "--output-root", output.toString()
        );

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Map<String,String> env = pb.environment();
        requireEnv(env, "DB_URL");
        requireEnv(env, "DB_USERNAME");
        requireEnv(env, "DB_PASSWORD");

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new KnowledgePreparationResult(cmd.runId(), "FAILED", 124, "",
                    "Knowledge preparation timed out after " + timeout.toMinutes() + " minutes");
            }
            String outputText = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.exitValue();
            return new KnowledgePreparationResult(cmd.runId(), code == 0 ? "COMPLETED" : "FAILED",
                code, outputText, code == 0 ? "" : outputText);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start knowledge preparation Python process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Knowledge preparation interrupted", e);
        }
    }

    private static void requireFile(Path path, String label) {
        if (!Files.isRegularFile(path)) throw new IllegalStateException(label + " not found: " + path);
    }

    private static void requireEnv(Map<String,String> env, String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required");
        env.put(key, value);
    }
}
