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