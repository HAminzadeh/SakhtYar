package com.sakhtyar.persian;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public final class PersianIntelligenceDependencyManager {
    private final Path repoRoot;

    public PersianIntelligenceDependencyManager(
            @Value("${app.persian-intelligence.repo-root:..}") String repoRoot) {
        this.repoRoot = Path.of(repoRoot).toAbsolutePath().normalize();
    }

    public List<PersianIntelligenceCapability> inspect() {
        List<PersianIntelligenceCapability> result = new ArrayList<>();
        Path python = repoRoot.resolve(".local/venv-persian-intelligence/Scripts/python.exe");
        Path registry = repoRoot.resolve("tools/persian-intelligence/model-registry.json");
        result.add(capability("PYTHON_RUNTIME", Files.isRegularFile(python), python.toString()));
        result.add(capability("MODEL_REGISTRY", Files.isRegularFile(registry), registry.toString()));
        result.add(capability("PYTHON_CORE",
                Files.isRegularFile(repoRoot.resolve("tools/persian-intelligence/sakhtyar_persian/dependencies.py")),
                "RuntimeDependencyManager"));
        return List.copyOf(result);
    }

    public boolean readyForCore() {
        return inspect().stream()
                .filter(x -> x.name().equals("PYTHON_RUNTIME") || x.name().equals("PYTHON_CORE"))
                .allMatch(x -> x.status() == PersianIntelligenceCapability.Status.READY);
    }

    private static PersianIntelligenceCapability capability(String name, boolean ready, String detail) {
        return new PersianIntelligenceCapability(name,
                ready ? PersianIntelligenceCapability.Status.READY
                      : PersianIntelligenceCapability.Status.MISSING,
                detail);
    }
}