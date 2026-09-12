-- fraud system tables

CREATE TABLE evaluated_transactions (
    id               VARCHAR(64) PRIMARY KEY,
    account_id       VARCHAR(64) NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    area_code        VARCHAR(16),
    created_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_evaluated_transactions_account_created
    ON evaluated_transactions (account_id, created_at);

CREATE INDEX idx_evaluated_transactions_created_at
    ON evaluated_transactions (created_at);

CREATE TABLE rule_hits (
    id             BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    rule_type      VARCHAR(32) NOT NULL,
    status         VARCHAR(24) NOT NULL,
    flagged        BOOLEAN NOT NULL,
    reason         VARCHAR(255),
    risk_level     INTEGER NOT NULL,
    evaluated_at   TIMESTAMP NOT NULL,
    CONSTRAINT uq_rule_hits_transaction_rule UNIQUE (transaction_id, rule_type)
);

CREATE INDEX idx_rule_hits_rule_type ON rule_hits (rule_type);
CREATE INDEX idx_rule_hits_flagged ON rule_hits (flagged);

CREATE TABLE bad_beneficiaries (
    id                    BIGSERIAL PRIMARY KEY,
    account_number        VARCHAR(64) NOT NULL,
    branch_code           VARCHAR(16) NOT NULL,
    status                VARCHAR(16) NOT NULL,
    added_by              VARCHAR(64) NOT NULL,
    added_at              TIMESTAMP NOT NULL,
    reason                VARCHAR(255),
    source_transaction_id VARCHAR(64)
);

CREATE INDEX idx_bad_beneficiaries_account
    ON bad_beneficiaries (account_number, branch_code);

CREATE TABLE bad_locations (
    id                    BIGSERIAL PRIMARY KEY,
    area_code             VARCHAR(16) NOT NULL,
    status                VARCHAR(16) NOT NULL,
    added_by              VARCHAR(64) NOT NULL,
    added_at              TIMESTAMP NOT NULL,
    reason                VARCHAR(255),
    source_transaction_id VARCHAR(64)
);

CREATE INDEX idx_bad_locations_area_code
    ON bad_locations (area_code);
