-- Flyway orders 2.1 after 2 and before UC-04's 3, so the agent ships
-- with UC-01 without renumbering anything later.

ALTER TABLE payment
    ADD COLUMN proposed_via VARCHAR(16) NOT NULL DEFAULT 'API'
        CHECK (proposed_via IN ('API', 'AGENT')),
    ADD COLUMN proposed_by  VARCHAR(80) NOT NULL DEFAULT 'unknown';

CREATE TABLE agent_turn (
    turn_id          UUID PRIMARY KEY,
    client_id        VARCHAR(64) NOT NULL,   -- operator or agent principal
    conversation_id  VARCHAR(64) NOT NULL,   -- task id for autonomous agents
    trigger          VARCHAR(16) NOT NULL,   -- CHAT / TASK
    model            VARCHAR(64) NOT NULL,
    prompt_version   VARCHAR(12) NOT NULL,   -- SHA-256 of the system prompt, first 12
    prompt_sha256    VARCHAR(64) NOT NULL,
    reply_sha256     VARCHAR(64),
    reply_flag       VARCHAR(24),            -- CONTRADICTS_FACTS, or null
    tool_calls       JSONB NOT NULL,         -- [{"tool":"propose_supplier_payment","paymentId":"…","result":"BLOCKED"}]
    documents_read   JSONB NOT NULL DEFAULT '[]',
    outcome          VARCHAR(24) NOT NULL,   -- ANSWERED / MODEL_UNAVAILABLE / BUDGET_EXHAUSTED
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_agent_turn_conversation ON agent_turn (client_id, conversation_id, created_at);

-- No foreign key to agent_turn: claims are written inside the tool call,
-- the turn row when the model has answered.
CREATE TABLE proposal_claim (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   UUID NOT NULL REFERENCES payment (id),
    turn_id      UUID NOT NULL,
    decision_id  BIGINT REFERENCES payment_decision (id),   -- the decision it was checked against
    subject      VARCHAR(32) NOT NULL,      -- APPROVAL_AUTHORITY … AVAILABLE_FUNDS, ROUTE, URGENCY
    source       VARCHAR(24) NOT NULL,      -- OPERATOR / AGENT_STATEMENT / RETRIEVED_DOCUMENT
    document_id  VARCHAR(64),
    statement    VARCHAR(300) NOT NULL,     -- digit runs of 6+ masked to the last four
    verdict      VARCHAR(16) NOT NULL,      -- CONFIRMED / CONTRADICTED / UNVERIFIABLE / NOT_ACCEPTED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_claim_document CHECK (source <> 'RETRIEVED_DOCUMENT' OR document_id IS NOT NULL)
);
CREATE INDEX ix_claim_payment ON proposal_claim (payment_id);
