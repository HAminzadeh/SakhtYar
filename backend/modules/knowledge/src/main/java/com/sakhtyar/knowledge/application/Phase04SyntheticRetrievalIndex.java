package com.sakhtyar.knowledge.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** SOURCE_ONLY scoped synthetic index; never represents a published legal citation. */
public final class Phase04SyntheticRetrievalIndex {
    public record Citation(UUID workspace, String version, String sourceSha256,
                           int page, String text, String status) {}
    private final List<Citation> documents = new ArrayList<>();

    public void add(Citation source) {
        if (source == null || source.workspace() == null || source.version() == null ||
            source.version().isBlank() || source.page() < 1 || source.text() == null ||
            source.sourceSha256() == null || !source.sourceSha256().matches("(?i)[a-f0-9]{64}") ||
            !"SYNTHETIC".equals(source.status()))
            throw new SecurityException("Unverifiable synthetic citation");
        documents.add(source);
    }
    public List<Citation> search(UUID workspace, String pinnedVersion, String query) {
        if (workspace == null || pinnedVersion == null || pinnedVersion.isBlank() ||
            query == null || query.isBlank()) throw new SecurityException("Search scope missing");
        String normalized = KnowledgeTextNormalizer.normalize(query);
        return documents.stream().filter(s -> s.workspace().equals(workspace) &&
            s.version().equals(pinnedVersion) &&
            KnowledgeTextNormalizer.normalize(s.text()).contains(normalized)).toList();
    }
}
