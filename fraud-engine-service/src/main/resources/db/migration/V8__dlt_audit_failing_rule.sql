-- every rule's consumer group dead-letters its own copy, so record which one failed
ALTER TABLE dlt_audit_log ADD COLUMN original_consumer_group VARCHAR(64);
ALTER TABLE dlt_audit_log ADD COLUMN exception_cause_class VARCHAR(255);

-- "how many records failed" is distinct (partition, offset); "which rule" is the consumer group
CREATE INDEX idx_dlt_audit_log_original_record
    ON dlt_audit_log (original_topic, original_partition, original_offset);
