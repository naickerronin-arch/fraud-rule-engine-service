-- area codes are four digit South African postal codes
ALTER TABLE evaluated_transactions ALTER COLUMN area_code TYPE VARCHAR(4);
ALTER TABLE bad_locations ALTER COLUMN area_code TYPE VARCHAR(4);
