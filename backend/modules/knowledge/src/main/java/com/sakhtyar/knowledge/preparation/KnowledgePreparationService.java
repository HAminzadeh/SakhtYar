package com.sakhtyar.knowledge.preparation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgePreparationService {
    private final KnowledgePreparationRunRepository runs;
    private final PythonKnowledgePreparationProcess python;

    public KnowledgePreparationService(KnowledgePreparationRunRepository runs,
                                       PythonKnowledgePreparationProcess python) {
        this.runs = runs;
        this.python = python;
    }

    public KnowledgePreparationResult prepare(Path inputRoot, Path outputRoot) {
        UUID workflowId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID runId = runs.create(workflowId, correlationId);

        var result = python.run(new KnowledgePreparationCommand(
            runId, workflowId, correlationId, inputRoot, outputRoot));

        if (result.exitCode() != 0) {
            runs.fail(runId, result.stderr());
            throw new IllegalStateException("Knowledge preparation failed: " + result.stderr());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> status(UUID runId) { return runs.get(runId); }
}
