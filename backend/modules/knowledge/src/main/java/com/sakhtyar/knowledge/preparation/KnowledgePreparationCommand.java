package com.sakhtyar.knowledge.preparation;

import java.nio.file.Path;
import java.util.UUID;

public record KnowledgePreparationCommand(
    UUID runId,
    UUID workflowId,
    UUID correlationId,
    Path inputRoot,
    Path outputRoot
) {}
