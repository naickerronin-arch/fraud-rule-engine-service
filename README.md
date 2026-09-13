# Fraud Rule Engine

A transaction fraud-detection service for internal fraud/compliance tooling: transactions
flow in over Kafka, get evaluated independently by multiple fraud rules, and a location's
own fraud rate self-tunes live from that evaluation history — all behind a gateway
enforcing JWT auth and role-based access, with human override as a correction, not a gate.

## Architecture summary

**Services:**

- **`api-gateway`** — the only host-exposed entry point (`:8888`). Validates JWTs against
  Dex, derives a role (`FraudTeam` / `ComplianceTeam`) from the token's `aud` claim, and
  routes to the two internal services, with a 5s response timeout.
- **`login-service`** — dev/demo convenience only, no host port of its own (reached through
  the gateway at `/dexLogin/**`). Wraps a password-grant call to Dex so grading doesn't
  require hand-rolling the token request.
- **`fraud-engine-service`** — the core domain. Three independent Kafka consumers (one per
  rule, each its own consumer group so every rule gets the full transaction stream), the
  Postgres-backed evaluation/storage layer, and the compliance/fraud-admin REST API.
- **`dex`** — OIDC identity provider, standing in for whatever enterprise IdP a real
  deployment would already have. No host port — only the gateway and login-service talk
  to it, never a browser directly.
- **`kafka` / `kafka-init`** — single-broker KRaft cluster (no Zookeeper); `kafka-init` is a
  one-shot container that creates the transaction-created/DLT/fraud-check-complete topics
  before anything else starts.
- **`fraud-db` (Postgres)** — primary datastore.
- **`prometheus`, `tempo`, `grafana`** — metrics, traces, and one dashboard UI over both.

**Event flow:** transactions arrive on `${environment}-transaction-created` (6 partitions,
keyed for per-account ordering). Each rule's consumer independently upserts a base
`evaluated_transactions` row, checks whether it's enabled for that transaction's type,
evaluates, and writes its own `rule_hits` row. Whichever rule's write is the *last* one
needed for that transaction (a config-driven expected-rule-count check, not a polling cron)
fires a `fraud.check.complete` event via a transactional outbox — avoiding a dual-write
between the DB commit and the Kafka publish.

**Active rules** (`fraud-engine-service/.../rule/strategy/`):
- **Velocity** — flags an account exceeding a transaction-count threshold within a rolling
  window. Standalone-capable: confident enough on its own evidence to flag a transaction
  by itself.
- **Behavioral deviation** — flags a transaction whose amount deviates from an account's
  own historical baseline, with a cohort-level fallback baseline (same transaction type,
  last 30 days) so new accounts aren't unprotected from transaction #1. The baseline
  leaves out the transaction being checked and anything flagged as fraud. Also
  standalone-capable.

Rules only look at transactions up to the timestamp of the one being evaluated, so a rule
that's behind the others doesn't count transactions that came after it.
- **Location** — computes each area code's fraud rate live, on every transaction
  (`% of that area's transactions flagged`), with a minimum-transaction-count floor so a
  handful of transactions can't produce a statistically meaningless rate. **Corroboration-only,
  not standalone-capable**: a bad-location match alone can never flag a transaction — Location
  detects "this is a known-bad area," not "this specific transaction is fraud," so it only
  contributes to the weighted score (see below). `canFlagStandalone()` on the `FraudRule`
  interface defaults to `true`; Location is the one rule that overrides it to `false`, and a
  new rule can opt into the same corroboration-only behavior without touching any dispatch
  logic elsewhere.

**Fraud verdict — hybrid standalone-or-corroborated, not a flat OR across rules:**
a transaction is flagged if *either* a standalone-capable rule is independently confident
enough to say so, *or* the combined weighted score across all rules (including
corroboration-only ones) crosses `overallFlagThreshold`. The weighted score itself is
normalized by the weights of the rules that actually evaluated for that transaction's type
(not a fixed assumed total) — `ATM_WITHDRAWAL` only runs two of the three rules, so it's
normalized against those two rules' weights, not silently capped below what `TRANSFER`
(all three rules) can reach for equivalent severity.

**No human confirmation gate anywhere in this loop.** Detection and location-tiering are
both fully automatic and live — no cron, no maker step blocking a location's fraud rate
from being recognized. The one remaining human action is `ComplianceTeam` **overriding** a
specific transaction's verdict after the fact (`POST /admin/transactions/{id}/override`) —
a correction, not a gate. The override never overwrites the system's own verdict; both are
kept (`evaluated_transactions.flagged` vs `.overridden_flagged`), and every reader
(the API, Location's own rate calculation) treats `COALESCE(overridden_flagged, flagged)`
as the effective verdict — so a correction actually changes what counts as fraud for
scoring purposes, without destroying the original evidence of what the system concluded.

**Storage** (Flyway-managed Postgres): `evaluated_transactions` (every transaction seen,
pass or fail, plus the system's own verdict and any human override), `rule_hits` (one row
per transaction/rule, deliberately normalized over JSONB for queryability), `bad_locations`
(a live, continuously-recomputed snapshot of each area code's current fraud-rate level, not
a maker-checker workflow table), `dlt_audit_log` (forensic metadata for anything that
failed deserialization/validation badly enough to reach the DLT, including which consumer
group sent it), and `outbox_events` / `dead_letter` (transactional outbox for the completion
event, rows are kept as an audit trail with a `PENDING` / `PUBLISHED` / `FAILED` status).

## Key trade-offs

- **One JVM hosting multiple Kafka consumer groups, not five separate microservices.**
  Real fault/offset isolation per rule (a crashing rule doesn't corrupt another rule's
  offsets), but a container crash still takes all rules down together. True process-level
  isolation wasn't judged worth 5x the deployment surface for this scope.
- **Password grant against Dex's `local` connector, not Client Credentials.** Client
  Credentials is the textbook machine-to-machine pattern, but the pulled Dex image doesn't
  support it (confirmed against a live instance, not assumed). Documented explicitly as a
  Dex-specific limitation, not a demo shortcut standing in for a different production
  pattern.
- **No schema registry (Jackson + Bean Validation DTOs, not Avro).** Considered and
  rejected as more infrastructure than this scope justifies.
- **Kafka replication factor 1.** Explicit local-demo limitation — a real deployment would
  run RF=3 with `min.insync.replicas=2`.
- **Rate limiting dropped entirely.** Reconsidered once the caller set was reframed as
  internal-only (fraud/compliance tooling, not public traffic) — a small set of known,
  trusted callers removes the threat rate limiting mainly defends against.
- **No circuit breaker at the gateway, just timeouts.** Only the REST API goes through the
  gateway (fraud detection itself runs over Kafka), the gateway is non-blocking, and traffic
  from internal tooling is low, so a breaker added little over a plain response timeout.
  Internal callers own their own retries and timeouts. Worth adding back if callers start
  retrying hard against a struggling `fraud-engine-service`.
- **No dedicated "recorder" consumer for the base transaction row.** Considered (would
  insert the base row exactly once instead of N racing rule-consumer attempts) and
  rejected — it introduces a real ordering race against the independent rule consumers,
  concretely dangerous for anything relying on the row already existing. Each rule handler
  does its own idempotent `INSERT ... ON CONFLICT DO NOTHING` instead; "is evaluation
  complete" is derived on read (comparing hit count to expected rule count) rather than
  stored as mutable state, so there's no completion flag that could silently fail to
  update.
- **Fan-in completion is decided under a row lock, not just "usually fine."** With 3
  independent rule consumers racing to detect "am I last," a naive read-then-write could
  let two of them both publish, or let two finishing at the same time each miss the other's
  hit so nobody publishes. Each rule locks the transaction row (`SELECT ... FOR UPDATE`)
  before counting hits, so whoever holds the lock sees every committed hit. The first rule
  to find them all sets `flagged`; any rule after it sees `flagged` already set and backs
  off. The verdict and the outbox row are written in the same database transaction.
- **Location auto-tiers off any rule hit now, deliberately — but only because it lost
  standalone authority at the same time.** An earlier version of this design required
  human-confirmed fraud (not any rule hit) before auto-promoting a location, specifically
  because a noisier signal risked blacklisting busy-but-legitimate or cold-start-heavy
  areas. That objection assumed a bad-location match could single-handedly flag a
  transaction. Once Location was made corroboration-only (see above), the blast radius of
  a wrongly-tiered area shrank to "nudges a weighted score," not "flags everyone there" —
  which is what made removing the human gate defensible without reintroducing the
  false-positive risk the original design was guarding against.
- **Location's fraud rate is computed live, per transaction, over all-time history — not a
  rolling window.** An area that had a bad month a year ago and has been clean since stays
  at whatever level that history produces, diluting only as new clean transactions
  accumulate. A time-windowed rate would decay faster; left as a known simplification, not
  implemented for this scope.
- **TLS terminates at the gateway only; internal traffic is plain HTTP.** The internal
  Docker network is unreachable from outside except through the gateway. A real deployment
  would terminate TLS at the gateway/load balancer identically — this is a stated local-demo
  simplification, not a different security posture.

## Running locally

```bash
cp .env.example .env
docker compose up --build
```

Get a token (through the gateway — `login-service` has no port of its own):

```bash
curl -X POST http://localhost:8888/dexLogin/auth \
  -H "Content-Type: application/json" \
  -d '{"username":"fraud-portal","password":"local-dev-password"}'
```

(`compliance-portal` is the other demo login, same password — use it for the
`ComplianceTeam`-gated endpoints.)

Use the returned `access_token` against the API:

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8888/fraud-service/transactions
```

**Swagger UI:**
- `fraud-engine-service`: http://localhost:8888/fraud-service/swagger-ui.html
- `login-service`: http://localhost:8888/dexLogin/swagger-ui.html

Both are wired with a Bearer auth scheme — paste a token into "Authorize" and "Try it out"
works directly in the browser.

**Grafana:** http://localhost:3000 — one dashboard (`Fraud Engine Overview`) covering
service health, HTTP latency, JVM heap, Kafka consumer lag, and per-rule hit rate
(`fraud_rule_evaluated_total{rule,flagged}`).

**Pushing a test transaction directly onto Kafka** (bypassing the API, to exercise rule
evaluation in isolation):

```bash
echo '{"transactionId":"txn-001","accountNumber":"ACC-1001","amount":250.00,"timestamp":"2026-01-01T12:00:00Z","transactionType":"TRANSFER","areaCode":"JHB-001"}' \
  | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
      --broker-list localhost:9092 --topic local-transaction-created
```

## Testing

Automated tests (unit + Testcontainers-backed Kafka/Postgres integration tests) were part
of the original design intent but are **not yet implemented** in this submission —
end-to-end behavior was instead verified manually: pushing transactions directly onto
Kafka and confirming the resulting `evaluated_transactions`/`rule_hits` rows, exercising
every API endpoint through the gateway with real tokens for both roles, and confirming
`FraudTeam` is correctly rejected (`403`) from the `ComplianceTeam`-only override endpoint.

No CI pipeline is wired up in this submission either, for the same reason.
