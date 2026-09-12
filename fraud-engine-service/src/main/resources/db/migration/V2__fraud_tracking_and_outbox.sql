-- Fraud confirmation audit trail and event publishing

CREATE TABLE fraud_confirmations (
    id             BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    confirmed      BOOLEAN NOT NULL,
    confirmed_by   VARCHAR(64) NOT NULL,
    confirmed_at   TIMESTAMP NOT NULL,
    reason         VARCHAR(255)
);

CREATE INDEX idx_fraud_confirmations_transaction ON fraud_confirmations (transaction_id);
CREATE INDEX idx_fraud_confirmations_confirmed ON fraud_confirmations (confirmed);

CREATE UNIQUE INDEX idx_bad_locations_area_code_open
    ON bad_locations (area_code)
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    partition_key  VARCHAR(64) NOT NULL,
    payload        TEXT NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    published_at   TIMESTAMP
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;

-- DLT audit log for dead-lettered messages
CREATE TABLE dlt_audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    original_topic      VARCHAR(128),
    original_partition  INTEGER,
    original_offset     BIGINT,
    exception_class     VARCHAR(255),
    exception_message   VARCHAR(1000),
    raw_payload         TEXT,
    received_at         TIMESTAMP NOT NULL
);

CREATE INDEX idx_dlt_audit_log_received_at ON dlt_audit_log (received_at);
