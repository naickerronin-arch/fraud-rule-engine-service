-- Local data: 500 past transactions with verdicts, so every event in scenarios.txt gets the same
-- verdict on every run. The rules read history from evaluated_transactions only, so no rule_hits
-- are seeded. Everything sits before 2026-01-15 12:00, the timestamp of every scenario event.
-- Re-running it resets the seeded rows and removes any scenario results.

BEGIN;

DELETE FROM outbox_events WHERE aggregate_id LIKE 'scn-%';
DELETE FROM rule_hits WHERE transaction_id LIKE 'scn-%';
DELETE FROM evaluated_transactions WHERE id LIKE 'seed-%' OR id LIKE 'scn-%';

-- 270 ordinary transactions: 45 accounts, days apart, spread over four areas, all clear
INSERT INTO evaluated_transactions (id, account_id, amount, transaction_type, area_code, event_time, flagged)
SELECT format('seed-bg-%s-%s', lpad(a::text, 2, '0'), n),
       format('SEED-%s', lpad(a::text, 2, '0')),
       100 + ((a * 37 + n * 53) % 190) * 10,
       CASE WHEN (a + n) % 3 = 0 THEN 'ATM_WITHDRAWAL' ELSE 'TRANSFER' END,
       (ARRAY['2196', '8001', '4001', '0002'])[1 + (a + n) % 4],
       timestamp '2026-01-15 12:00:00' - make_interval(days => n * 4 + a % 4, hours => a % 24),
       false
FROM generate_series(1, 45) a, generate_series(1, 6) n;

-- area 2001: 85 of 100 withdrawals flagged, enough to stay in the top tier (80%+) after a few new clear ones
INSERT INTO evaluated_transactions (id, account_id, amount, transaction_type, area_code, event_time, flagged)
SELECT format('seed-hot-%s', lpad(i::text, 3, '0')),
       format('SEED-HOT-%s', lpad((i % 20)::text, 2, '0')),
       200,
       'ATM_WITHDRAWAL',
       '2001',
       timestamp '2026-01-15 12:00:00' - make_interval(hours => i * 6),
       i > 15
FROM generate_series(1, 100) i;

-- scenario history: amounts cycle 180-220 (mean 200, sample sd 14.4), one a day,
-- plus `recent` transactions in the minutes before 12:00 for the velocity scenarios
INSERT INTO evaluated_transactions (id, account_id, amount, transaction_type, area_code, event_time, flagged)
SELECT format('seed-%s-%s', s.name, lpad(n::text, 2, '0')),
       s.account,
       200 + (n % 5 - 2) * 10,
       s.transaction_type,
       '2196',
       timestamp '2026-01-15 12:00:00'
           - CASE WHEN n <= s.recent THEN make_interval(mins => n) ELSE make_interval(days => n - s.recent) END,
       false
FROM (VALUES ('clear',  'ACC-CLEAR',  'TRANSFER',       30, 0),
             ('burst',  'ACC-BURST',  'TRANSFER',       34, 9),
             ('spike',  'ACC-SPIKE',  'TRANSFER',       30, 0),
             ('hotvel', 'ACC-HOTVEL', 'ATM_WITHDRAWAL', 6,  6),
             ('noarea', 'ACC-NOAREA', 'TRANSFER',       30, 6)) AS s(name, account, transaction_type, total, recent),
     generate_series(1, s.total) n;

COMMIT;

SELECT count(*) AS seeded,
       count(*) FILTER (WHERE flagged) AS flagged
FROM evaluated_transactions
WHERE id LIKE 'seed-%';
