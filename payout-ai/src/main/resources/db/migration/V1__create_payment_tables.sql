CREATE TABLE payment (
    id                      UUID PRIMARY KEY,
    legal_entity_id         VARCHAR(32) NOT NULL,
    supplier_id             VARCHAR(64) NOT NULL,
    normalized_invoice      VARCHAR(64) NOT NULL,
    obligation_id           VARCHAR(64) NOT NULL,
    purchase_order_id       VARCHAR(64),                 -- reporting only, not in the key
    amount_paise           BIGINT NOT NULL CHECK (amount_paise > 0),
    currency                VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    beneficiary_fingerprint VARCHAR(64) NOT NULL,
    decision_status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    status                  VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
    validating_since        TIMESTAMPTZ,                 -- evaluation lease, step 5
    version                 BIGINT NOT NULL DEFAULT 0,   -- optimistic check
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payment_economic_action
        UNIQUE (legal_entity_id, supplier_id, normalized_invoice, obligation_id)
);
-- All payments against one purchase order, e.g. both instalments of PO-94018.
CREATE INDEX ix_payment_purchase_order ON payment (legal_entity_id, purchase_order_id);

-- Original references, verbatim: INV-94018 and INV94018 both land here.
CREATE TABLE invoice_alias (
    payment_id      UUID NOT NULL REFERENCES payment (id),
    raw_reference   VARCHAR(64) NOT NULL,
    submitted_by    VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (payment_id, raw_reference, submitted_by)
);

-- Client retries. Same key + same hash = replay; same key + different hash = conflict.
CREATE TABLE api_idempotency (
    client_id        VARCHAR(64) NOT NULL,
    idempotency_key  VARCHAR(80) NOT NULL,
    request_hash     VARCHAR(64) NOT NULL,
    payment_id       UUID NOT NULL REFERENCES payment (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id, idempotency_key)
);