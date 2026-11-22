package com.sakhtyar.knowledge.retrieval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface KnowledgeRetrievalEngine {
    EvidencePack retrieve(UUID releaseId, String query, int limit);

    record EvidencePack(
        UUID releaseId,
        String query,
        String retrievalVersion,
        List<Map<String,Object>> structuredRules,
        List<Map<String,Object>> fullTextMatches,
        List<Map<String,Object>> graphContext,
        List<Map<String,Object>> evidence
    ) {}
}