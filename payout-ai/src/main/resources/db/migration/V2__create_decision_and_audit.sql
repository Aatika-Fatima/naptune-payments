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
