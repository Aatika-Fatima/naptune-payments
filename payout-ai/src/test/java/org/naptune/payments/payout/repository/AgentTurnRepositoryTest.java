package org.naptune.payments.payout.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.entity.AgentTurn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Build step 10 "Done when": AgentTurn validates against agent_turn, its two
// JSONB columns round-trip, and a conversation's turns come back in order.
//
// The data is the scenario turn: ops-1 pastes the supplier email, the model
// reads it, proposes INV-94018 and explains the block. Each test flushes and
// clears the persistence context so reads come from Postgres, then rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AgentTurnRepositoryTest {

    private static final String PROMPT_SHA = "9c1f0e7a2b4d" + "0".repeat(52);

    @Autowired
    AgentTurnRepository turns;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JdbcClient jdbc;

    // Use case: the scenario turn is recorded once the model has answered: the
    // tools it called with their results and the email it read. Prompt and
    // reply are hashes only.
    @Test
    void scenarioTurnRoundTripsItsToolCallsAndDocuments() {
        UUID turnId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        turns.save(new AgentTurn(turnId, "ops-1", "conv-1", "CHAT", "gemini-2.5-flash",
                PROMPT_SHA.substring(0, 12), PROMPT_SHA, "a".repeat(64), null,
                List.of(
                        Map.of("tool", "search_enterprise_documents", "result", "1 document"),
                        Map.of("tool", "propose_supplier_payment", "paymentId", paymentId.toString(),
                                "result", "BLOCKED"),
                        Map.of("tool", "explain_decision", "paymentId", paymentId.toString(), "result", "BLOCKED")),
                List.of("mail-7f3a"), "ANSWERED"));
        reloadFromDatabase();

        AgentTurn turn = turns.findById(turnId).orElseThrow();
        assertThat(turn.getClientId()).isEqualTo("ops-1");
        assertThat(turn.getPromptVersion()).isEqualTo("9c1f0e7a2b4d");
        assertThat(turn.getReplyFlag()).isNull();
        assertThat(turn.getOutcome()).isEqualTo("ANSWERED");
        assertThat(turn.getDocumentsRead()).containsExactly("mail-7f3a");
        assertThat(turn.getToolCalls())
                .extracting(call -> call.get("tool"))
                .containsExactly("search_enterprise_documents", "propose_supplier_payment", "explain_decision");
        assertThat(turn.getToolCalls().get(1)).containsEntry("paymentId", paymentId.toString());
        assertThat(turn.getCreatedAt()).isNotNull();
    }

    // Use case: the model is unreachable. The only write is a turn with no
    // reply, no tool calls and outcome MODEL_UNAVAILABLE (AI-06).
    @Test
    void modelUnavailableTurnHasNoReplyAndNoToolCalls() {
        UUID turnId = UUID.randomUUID();

        turns.save(new AgentTurn(turnId, "ops-1", "conv-1", "CHAT", "gemini-2.5-flash",
                PROMPT_SHA.substring(0, 12), PROMPT_SHA, null, null, List.of(), List.of(), "MODEL_UNAVAILABLE"));
        reloadFromDatabase();

        AgentTurn turn = turns.findById(turnId).orElseThrow();
        assertThat(turn.getReplySha256()).isNull();
        assertThat(turn.getToolCalls()).isEmpty();
        assertThat(turn.getDocumentsRead()).isEmpty();
    }

    // documents_read has a column default, for writers that do not name it.
    @Test
    void documentsReadDefaultsToAnEmptyList() {
        UUID turnId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO agent_turn (turn_id, client_id, conversation_id, trigger, model,
                                        prompt_version, prompt_sha256, tool_calls, outcome)
                VALUES (?, 'agent-a', 'task-1', 'TASK', 'gemini-2.5-flash', ?, ?, '[]', 'ANSWERED')""")
                .params(turnId, PROMPT_SHA.substring(0, 12), PROMPT_SHA)
                .update();
        reloadFromDatabase();

        assertThat(turns.findById(turnId).orElseThrow().getDocumentsRead()).isEmpty();
    }

    // Use case: a redelivered task looks up its conversation's turns. Another
    // client's turns in a same-named conversation are not included. (Order by
    // created_at is not checked here: now() is fixed within one transaction.)
    @Test
    void conversationTurnsAreScopedToTheClient() {
        UUID first = saveTurn("agent-a", "task-1");
        UUID second = saveTurn("agent-a", "task-1");
        saveTurn("agent-b", "task-1");
        reloadFromDatabase();

        assertThat(turns.findByClientIdAndConversationIdOrderByCreatedAt("agent-a", "task-1"))
                .extracting(AgentTurn::getTurnId)
                .containsExactlyInAnyOrder(first, second);
    }

    private UUID saveTurn(String clientId, String conversationId) {
        UUID turnId = UUID.randomUUID();
        turns.save(new AgentTurn(turnId, clientId, conversationId, "TASK", "gemini-2.5-flash",
                PROMPT_SHA.substring(0, 12), PROMPT_SHA, "b".repeat(64), null, List.of(), List.of(), "ANSWERED"));
        return turnId;
    }

    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }
}
