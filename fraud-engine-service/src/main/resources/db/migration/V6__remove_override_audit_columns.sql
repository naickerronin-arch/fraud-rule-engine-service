--unused, remove to decrease scope
ALTER TABLE evaluated_transactions DROP COLUMN overridden_by;
ALTER TABLE evaluated_transactions DROP COLUMN override_reason;
