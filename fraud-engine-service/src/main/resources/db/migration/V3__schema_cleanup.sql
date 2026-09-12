-- Remove unused columns and tables

DROP TABLE bad_beneficiaries;

-- decrease scope and remove unused columns
ALTER TABLE bad_locations DROP COLUMN added_by;
ALTER TABLE bad_locations DROP COLUMN reason;
ALTER TABLE bad_locations DROP COLUMN source_transaction_id;
ALTER TABLE bad_locations RENAME COLUMN added_at TO created_at;
