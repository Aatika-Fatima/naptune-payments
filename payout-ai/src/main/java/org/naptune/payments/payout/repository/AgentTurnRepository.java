package org.naptune.payments.payout.repository;

import java.util.List;
import java.util.UUID;
import org.naptune.payments.payout.entity.AgentTurn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTurnRepository extends JpaRepository<AgentTurn, UUID> {

    // One conversation's turns in the order they happened (ix_agent_turn_conversation).
    // A redelivered task finds its recorded turn here and replays it (G11).
    List<AgentTurn> findByClientIdAndConversationIdOrderByCreatedAt(String clientId, String conversationId);
}
