package com.sakhtyar.knowledge.preparation;

import java.util.UUID;

public record KnowledgePreparationResult(
    UUID runId,
    String stage,
    int exitCode,
    String stdout,
    String stderr
) {}
