package com.sakhtyar.agents.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository
        extends JpaRepository<AgentMessageEntity, UUID> {

    List<AgentMessageEntity> findAllByConversationIdOrderByCreatedAtAsc(
            UUID conversationId
    );
}
