package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One row per agent turn: what the model was asked, read and called. Prompt and
// reply are kept as SHA-256 only: pasted emails carry account numbers. Written
// once, after the model has answered or failed.
@Entity
@Table(name = "agent_turn")
public class AgentTurn {

    @Id
    @Column(name = "turn_id")
    private UUID turnId;

    // The operator or agent principal.
    @Column(name = "client_id", nullable = false, updatable = false, length = 64)
    private String clientId;

    // Chat conversation id, or the task id for an autonomous agent.
    @Column(name = "conversation_id", nullable = false, updatable = false, length = 64)
    private String conversationId;

    // CHAT or TASK.
    @Column(nullable = false, updatable = false, length = 16)
    private String trigger;

    @Column(nullable = false, updatable = false, length = 64)
    private String model;

    // First 12 hex characters of prompt_sha256: a short label for metrics.
    @Column(name = "prompt_version", nullable = false, updatable = false, length = 12)
    private String promptVersion;

    @Column(name = "prompt_sha256", nullable = false, updatable = false, length = 64)
    private String promptSha256;

    // Null when the model gave no reply.
    @Column(name = "reply_sha256", updatable = false, length = 64)
    private String replySha256;

    // CONTRADICTS_FACTS when the reply guard caught the reply contradicting
    // recorded facts; otherwise null.
    @Column(name = "reply_flag", updatable = false, length = 24)
    private String replyFlag;

    // [{"tool":"propose_supplier_payment","paymentId":"…","result":"BLOCKED"}]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", nullable = false, updatable = false)
    private List<Map<String, Object>> toolCalls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents_read", nullable = false, updatable = false)
    private List<String> documentsRead;

    // ANSWERED, MODEL_UNAVAILABLE or BUDGET_EXHAUSTED.
    @Column(nullable = false, updatable = false, length = 24)
    private String outcome;

    // Filled by the column default.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AgentTurn() {
    }

    public AgentTurn(UUID turnId, String clientId, String conversationId, String trigger, String model,
                     String promptVersion, String promptSha256, String replySha256, String replyFlag,
                     List<Map<String, Object>> toolCalls, List<String> documentsRead, String outcome) {
        this.turnId = turnId;
        this.clientId = clientId;
        this.conversationId = conversationId;
        this.trigger = trigger;
        this.model = model;
        this.promptVersion = promptVersion;
        this.promptSha256 = promptSha256;
        this.replySha256 = replySha256;
        this.replyFlag = replyFlag;
        this.toolCalls = List.copyOf(toolCalls);
        this.documentsRead = List.copyOf(documentsRead);
        this.outcome = outcome;
    }

    // Getters only: a turn is recorded once and never edited.
    public UUID getTurnId() {
        return turnId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getTrigger() {
        return trigger;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getPromptSha256() {
        return promptSha256;
    }

    public String getReplySha256() {
        return replySha256;
    }

    public String getReplyFlag() {
        return replyFlag;
    }

    public List<Map<String, Object>> getToolCalls() {
        return toolCalls;
    }

    public List<String> getDocumentsRead() {
        return documentsRead;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
