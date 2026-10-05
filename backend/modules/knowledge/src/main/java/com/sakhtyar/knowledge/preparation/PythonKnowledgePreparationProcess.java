package com.sakhtyar.knowledge.preparation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PythonKnowledgePreparationProcess {
    private final Path python;
    private final Path engine;

    public PythonKnowledgePreparationProcess(
        @Value("${app.knowledge.preparation.python:.local/venv-knowledge-preparation/Scripts/python.exe}") String python,
        @Value("${app.knowledge.preparation.engine:tools/knowledge-preparation/pipeline.py}") String engine) {
        this.python = Path.of(python);
        this.engine = Path.of(engine);
    }

    public KnowledgePreparationResult run(KnowledgePreparationCommand cmd) {
        List<String> args = new ArrayList<>();
        args.add(python.toString());
        args.add(engine.toString());
        args.add("--run-id"); args.add(cmd.runId().toString());
        args.add("--workflow-id"); args.add(cmd.workflowId().toString());
        args.add("--correlation-id"); args.add(cmd.correlationId().toString());
        args.add("--input-root"); args.add(cmd.inputRoot().toString());
        args.add("--output-root"); args.add(cmd.outputRoot().toString());

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false);
        Map<String,String> env = pb.environment();
        copyEnv(env, "DB_URL");
        copyEnv(env, "DB_USERNAME");
        copyEnv(env, "DB_PASSWORD");

        try {
            Process p = pb.start();
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            return new KnowledgePreparationResult(cmd.runId(), code == 0 ? "ENRICHED" : "FAILED", code, stdout, stderr);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start knowledge preparation Python process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Knowledge preparation interrupted", e);
        }
    }

    private static void copyEnv(Map<String,String> env, String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) env.put(key, value);
    }
}
