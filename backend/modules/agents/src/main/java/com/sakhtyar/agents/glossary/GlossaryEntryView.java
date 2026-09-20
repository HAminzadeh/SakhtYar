package com.sakhtyar.agents.glossary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API projection for glossary entries.
 * Kept as a top-level record so controllers and other components can import it
 * without depending on an inner type of PersianGlossaryService.
 */
public record GlossaryEntryView(
        UUID id,
        String term,
        String meaning,
        List<String> aliases,
        GlossaryStatus status,
        String createdBy,
        String approvedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
