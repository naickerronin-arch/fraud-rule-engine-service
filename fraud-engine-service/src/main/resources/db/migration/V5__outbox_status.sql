-- Outbox rows are kept, status tracks where each one is

ALTER TABLE outbox_events ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
UPDATE outbox_events SET status = 'PUBLISHED' WHERE published_at IS NOT NULL;

DROP INDEX idx_outbox_events_unpublished;
CREATE INDEX idx_outbox_events_pending
    ON outbox_events (created_at)
    WHERE status = 'PENDING';
