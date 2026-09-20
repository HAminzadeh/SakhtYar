package com.sakhtyar.agents.glossary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlossaryEntryRepository
        extends JpaRepository<GlossaryEntryEntity, UUID> {

    Optional<GlossaryEntryEntity> findByNormalizedTerm(String normalizedTerm);

    List<GlossaryEntryEntity> findAllByStatusOrderByTermAsc(GlossaryStatus status);

    List<GlossaryEntryEntity> findAllByOrderByUpdatedAtDesc();
}
