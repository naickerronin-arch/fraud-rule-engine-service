-- created_at is when this service inserted the row, which is what "stuck for N minutes" is measured from;
-- event_time is the producer's timestamp, which the rules' history windows are measured on
ALTER TABLE evaluated_transactions ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE evaluated_transactions ADD COLUMN event_time TIMESTAMP NOT NULL;

DROP INDEX idx_evaluated_transactions_account_created;
DROP INDEX idx_evaluated_transactions_created_at;
CREATE INDEX idx_evaluated_transactions_account_event_time
    ON evaluated_transactions (account_id, event_time);
CREATE INDEX idx_evaluated_transactions_event_time
    ON evaluated_transactions (event_time);

-- set once the sweeper gives up, so the failure is published exactly once
ALTER TABLE evaluated_transactions ADD COLUMN abandoned_at TIMESTAMP;

CREATE INDEX idx_evaluated_transactions_awaiting_verdict
    ON evaluated_transactions (created_at)
    WHERE flagged IS NULL AND abandoned_at IS NULL;
