package com.sakhtyar.agents.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConversationRepository
        extends JpaRepository<AgentConversationEntity, UUID> {
}
