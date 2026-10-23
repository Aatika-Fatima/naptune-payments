-- Append-only answers from systems of record. Nothing the model says, and no
-- retrieved document, is ever written here.
CREATE TABLE evidence_record (
    id               UUID PRIMARY KEY,
    payment_id       UUID NOT NULL REFERENCES payment (id),
    payment_check_type VARCHAR(32) NOT NULL, -- APPROVAL_AUTHORITY / INVOICE / BENEFICIARY_ACCOUNT / COMPLIANCE_SCREENING / DUPLICATE_PAYMENT / AVAILABLE_FUNDS
    source           VARCHAR(40) NOT NULL,
    value            JSONB NOT NULL,
    source_version   VARCHAR(64),
    source_time      TIMESTAMPTZ,
    freshness        VARCHAR(16) NOT NULL,   -- FRESH / STALE / UNAVAILABLE / INCOMPLETE / CONFLICTING
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_evidence_payment ON evidence_record (payment_id);

CREATE TABLE payment_decision (
    id               BIGSERIAL PRIMARY KEY,
    payment_id       UUID NOT NULL REFERENCES payment (id),
    decision         VARCHAR(16) NOT NULL,
    reason_codes     JSONB NOT NULL,
    evidence_ids     JSONB NOT NULL,
    policy_version   VARCHAR(32) NOT NULL,
    valid_until      TIMESTAMPTZ NOT NULL,   -- the dispatch guard reads this in the next use case
    decided_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_decision_payment ON payment_decision (payment_id, id DESC);

CREATE TABLE audit_record (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   UUID NOT NULL,
    actor        VARCHAR(64) NOT NULL,
    action       VARCHAR(48) NOT NULL,
    from_state   VARCHAR(32),
    to_state     VARCHAR(32),
    detail       JSONB NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_payment ON audit_record (payment_id, id);
