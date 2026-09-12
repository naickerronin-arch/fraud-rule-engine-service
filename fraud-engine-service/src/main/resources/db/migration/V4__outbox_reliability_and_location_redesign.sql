-- Outbox retry tracking and location redesign

-- Add retry/backoff tracking to outbox
ALTER TABLE outbox_events ADD COLUMN attempt_count INTEGER;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(1000);
ALTER TABLE outbox_events ADD COLUMN next_retry TIMESTAMP;

-- Dead letter table for exhausted retries
CREATE TABLE dead_letter (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(64) NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    partition_key  VARCHAR(64) NOT NULL,
    payload        TEXT NOT NULL,
    last_error     VARCHAR(1000),
    attempt_count  INTEGER NOT NULL,
    created_at     TIMESTAMP,
    moved_at       TIMESTAMP NOT NULL
);

-- Remove fraud confirmation gate: locations are now computed live from transaction data
DROP TABLE fraud_confirmations;
DROP INDEX idx_bad_locations_area_code_open;

-- Redesign bad_locations: one row per area code with live-computed fraud level
ALTER TABLE bad_locations DROP COLUMN status;
ALTER TABLE bad_locations ADD COLUMN level INTEGER NOT NULL DEFAULT 0;
ALTER TABLE bad_locations ADD CONSTRAINT uq_bad_locations_area_code UNIQUE (area_code);

-- Track system verdict and human overrides
ALTER TABLE evaluated_transactions ADD COLUMN flagged BOOLEAN;
ALTER TABLE evaluated_transactions ADD COLUMN overridden_flagged BOOLEAN;
ALTER TABLE evaluated_transactions ADD COLUMN overridden_by VARCHAR(64);
ALTER TABLE evaluated_transactions ADD COLUMN overridden_at TIMESTAMP;
ALTER TABLE evaluated_transactions ADD COLUMN override_reason VARCHAR(255);

-- Index for location-based queries
CREATE INDEX idx_evaluated_transactions_area_code ON evaluated_transactions (area_code);
