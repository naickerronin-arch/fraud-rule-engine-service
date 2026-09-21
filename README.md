# Fraud Rule Engine

A transaction fraud-detection service for internal fraud/compliance tooling: transactions
flow in over Kafka, get evaluated independently by multiple fraud rules, and a location's
own fraud rate self-tunes live from that evaluation history — all behind a gateway
enforcing JWT auth and role-based access, with human override as a correction, not a gate.

## Architecture summary

**Request path** (REST, everything behind one host port):

```
                        ┌───────────────────────┐
  curl / Swagger UI     │   api-gateway :8888   │──/fraud-service/**──► fraud-engine-service :8081
          │  Bearer JWT │  validate the JWT     │──/dexLogin/**───────► login-service :8090
          └────────────►│  aud claim → role     │──/demo/**───────────► demo-service :8095
                        │  route, per-route     │
                        │  response timeouts    │──JWKS───────────────► dex :5556 (OIDC)
                        └───────────────────────┘
```

**Event path** (fraud detection itself, no REST involved):

```
  upstream producer (not in repo) · demo-service
                    │
                    ▼
  ┌───────────────────────────────────────────┐  keyed by account, so one account's
  │ local-transaction-created, 6 partitions   │  transactions stay in order
  └────┬────────────┬────────────┬────────────┘
       │            │            │
       ▼            ▼            ▼
   velocity    behavioural   location          one consumer group per rule, so
     group        group        group           every rule sees every transaction
       │            │            │
       └────────────┬────────────┘
                    │  validate · upsert the transaction row · write this rule's hit
                    ▼
  ┌───────────────────────────────────────────┐
  │ lock the transaction row and count the    │  Postgres holds evaluated_transactions,
  │ hits; whichever rule finds them all       │  rule_hits, bad_locations, outbox_events,
  │ writes the verdict and the outbox row     │  dlt_audit_log and dead_letter
  │ in one database transaction               │
  └─────────────────┬─────────────────────────┘
                    │  outbox relay, every 100 ms
                    ▼
                    local-fraud-check-complete  ───────────────►  downstream consumers

  deserialization or validation failure, retries exhausted
      local-transaction-created-dlt  ──► dlt audit group  ──►  dlt_audit_log
  no verdict after app.pending-evaluation.abandon-after-minutes
      pending sweeper  ──► local-fraud-check-failed  ─────►  downstream consumers
```

**Observability:** the gateway and the engine expose `/actuator/prometheus` and send OTLP spans
to Tempo; Grafana (`:3000`) reads both.

Only two ports reach the host: the gateway (`8888`) and Grafana (`3000`). Everything else talks
over the compose network.

**Services:**

- **`api-gateway`** — the only host-exposed entry point (`:8888`). Validates JWTs against
  Dex, derives a role (`FraudTeam` / `ComplianceTeam`) from the token's `aud` claim, enforces
  the `ComplianceTeam`-only override endpoint, and routes to the two internal services with a
  5s response timeout. Errors use Spring Boot's default error responses.
- **`login-service`** — dev/demo convenience only, no host port of its own (reached through
  the gateway at `/dexLogin/**`). Wraps a password-grant call to Dex so grading doesn't
  require hand-rolling the token request.
- **`fraud-engine-service`** — the core domain. Three independent Kafka consumers (one per
  rule, each its own consumer group so every rule gets the full transaction stream), the
  Postgres-backed evaluation/storage layer, the transactional outbox, and the
  compliance/fraud-admin REST API.
- **`dex`** — OIDC identity provider, standing in for whatever enterprise IdP a real
  deployment would already have. No host port — only the gateway and login-service talk
  to it, never a browser directly.
- **`kafka` / `kafka-init`** — single-broker KRaft cluster (no Zookeeper); `kafka-init` is a
  one-shot container that creates the transaction-created/DLT/fraud-check-complete topics
  before anything else starts.
- **`fraud-db` (Postgres)** — primary datastore.
- **`prometheus`, `tempo`, `grafana`** — metrics, traces, and one dashboard UI over both.

**Event flow:** transactions arrive on `${environment}-transaction-created` (6 partitions,
keyed for per-account ordering). Each rule's consumer validates the payload on consumption
(`@Valid`), then:

1. rejects transaction types with no rules configured (sent straight to the DLT),
2. upserts the base `evaluated_transactions` row (`INSERT ... ON CONFLICT DO NOTHING`),
3. evaluates its rule if it's enabled for that transaction type and writes its own `rule_hits` row,
4. locks the transaction row and checks whether every enabled rule has now reported.

The rule that finds all hits present sets the verdict (`flagged`) and writes a
`fraud-check-complete` event to the outbox **in the same database transaction**. A scheduled
relay publishes outbox rows to Kafka and marks them `PUBLISHED`, retrying with backoff and
marking them `FAILED` (plus a `dead_letter` row) after 5 attempts. Invalid or undeserializable
messages go to `${environment}-transaction-created-dlt` and are recorded in `dlt_audit_log`.

**Active rules** (`fraud-engine-service/.../rule/strategy/`):
- **Velocity** — flags an account exceeding a transaction-count threshold within a rolling
  window. Standalone-capable: confident enough on its own evidence to flag a transaction
  by itself.
- **Behavioral deviation** — flags a transaction whose amount deviates from an account's
  own historical baseline, with a cohort-level fallback baseline (same transaction type) so
  new accounts aren't unprotected from transaction #1. The baseline leaves out the
  transaction being checked, otherwise an outlier would drag its own average and standard
  deviation towards itself. Also standalone-capable.
- **Location** — computes each area code's fraud rate live, on every transaction
  (`% of that area's decided transactions that were flagged`, ignoring transactions still
  waiting for a verdict), with a minimum-transaction-count floor so a handful of transactions
  can't produce a statistically meaningless rate. **Corroboration-only, not
  standalone-capable**: a bad-location match alone can never flag a transaction — Location
  detects "this is a known-bad area," not "this specific transaction is fraud," so it only
  contributes to the weighted score (see below). `canFlagStandalone()` on the `FraudRule`
  interface defaults to `true`; Location is the one rule that overrides it to `false`, and a
  new rule can opt into the same corroboration-only behavior without touching any dispatch
  logic elsewhere.

Rule thresholds and weights live in `application.yml` under `app.velocity-config`,
`app.location-config` and `app.behavioral-deviation-config` (velocity 0.5, location 0.3,
behavioral 0.2).

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
specific transaction's verdict after the fact (`POST /admin/transactions/{id}/override` with
`{"flagged": true|false}`) — a correction, not a gate. The override never overwrites the
system's own verdict; both are kept (`evaluated_transactions.flagged` vs
`.overridden_flagged`, plus `overridden_at`), and every reader (the API, Location's own rate
calculation) treats `COALESCE(overridden_flagged, flagged)` as the effective verdict — so a
correction actually changes what counts as fraud for scoring purposes, without destroying the
original evidence of what the system concluded. The transactions API returns that effective
verdict as `status` (`FLAGGED`, `CLEAR` or `PENDING`) alongside the system and override values.

**Storage** (Flyway-managed Postgres): `evaluated_transactions` (every transaction seen,
pass or fail, plus the system's own verdict and any override), `rule_hits` (one row
per transaction/rule, deliberately normalized over JSONB for queryability), `bad_locations`
(a live, continuously-recomputed snapshot of each area code's current fraud-rate level, not
a maker-checker workflow table), `dlt_audit_log` (forensic metadata for anything that
failed deserialization/validation badly enough to reach the DLT), and `outbox_events` /
`dead_letter` (transactional outbox for the completion event; outbox rows are never deleted
and are kept as an audit trail with a `PENDING` / `PUBLISHED` / `FAILED` status).

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
- **Roles are enforced at the gateway only.** `fraud-engine-service` just requires a valid
  token; it isn't reachable from outside the Docker network, so the gateway is the single
  place role mappings live.
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
  does its own idempotent `INSERT ... ON CONFLICT DO NOTHING` instead.
- **Fan-in completion is decided under a row lock, not just "usually fine."** With 3
  independent rule consumers racing to detect "am I last," a naive read-then-write could
  let two of them both publish, or let two finishing at the same time each miss the other's
  hit so nobody publishes. Each rule locks the transaction row (`SELECT ... FOR UPDATE`)
  before counting hits, so whoever holds the lock sees every committed hit. The first rule
  to find them all sets `flagged`; any rule after it sees `flagged` already set and backs
  off. The verdict and the outbox row are written in the same database transaction. This
  works because every rule writes to the same database; if rules ever moved into separate
  services, an aggregator consuming per-rule results keyed by transaction would replace it.
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
- **Overrides record the new verdict and when, not who or why.** Free-text reason and
  "actioned by" columns were removed to keep scope down; a proper audit trail is future work.
- **Application metrics are recorded in one AOP aspect** (`FraudEngineMetricsAspect`) so the
  handlers stay free of metrics code. Pointcuts match on class/method names, which can break
  silently on a rename — `FraudEngineMetricsAspectTest` fails if an advice stops firing.
- **TLS terminates at the gateway only; internal traffic is plain HTTP.** The internal
  Docker network is unreachable from outside except through the gateway. A real deployment
  would terminate TLS at the gateway/load balancer identically — this is a stated local-demo
  simplification, not a different security posture.

## Known limitations

- Rule history isn't bounded to the evaluated transaction's timestamp. Because each rule is its
  own consumer group, a rule that falls behind the others can count transactions for the same
  account that arrived after the one it's evaluating.
- `fraud-engine-service`'s Prometheus metrics are reachable through the gateway without a token
  (`/fraud-service/actuator/prometheus`).
- The database connection pool defaults to 20 connections (`DB_POOL_SIZE`) for 24 consumer
  threads plus the outbox relay and the API; size it to the real load per environment.
- Both demo logins share one password, and wrong login credentials return `500` rather than `401`.

## Running locally

From the repository root:

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

**Pushing a test transaction directly onto Kafka** (bypassing the API, to exercise rule
evaluation in isolation):

```bash
echo '{"transactionId":"txn-001","accountNumber":"ACC-1001","amount":250.00,"timestamp":"2026-01-01T12:00:00Z","transactionType":"TRANSFER","areaCode":"JHB-001"}' \
  | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server localhost:9092 --topic local-transaction-created
```

## Observability

**Grafana:** http://localhost:3000 (first login `admin` / `admin`) — one dashboard,
`Fraud Rule Engine — Overview`, provisioned from `observability/dashboards/json`:

- service up/down, HTTP request rate and p95 latency, JVM heap, gateway 4xx/5xx
- transactions without a verdict and failed outbox events (both turn red at 1+)
- Kafka consumer lag per consumer group, DB connection pool usage
- rule-hit rate, rule errors and average evaluation time per rule
- fraud flag rate, completion rate, compliance overrides, DLT rate

**Application metrics** (all from `fraud-engine-service`):

| Metric | Tags | Recorded by |
|---|---|---|
| `fraud_rule_evaluated_total` | `rule`, `status` (`EVALUATED`, `SKIPPED_MISSING_DATA`, `ERROR`), `flagged` | aspect, around `FraudRule.evaluateRule` |
| `fraud_rule_duration_seconds` | `rule` | aspect, around `FraudRule.evaluateRule` |
| `fraud_check_complete_total` | `flagged` | aspect, after the verdict and outbox row commit |
| `fraud_transaction_override_total` | `flagged` | aspect, after an override |
| `fraud_dlt_received_total` | — | aspect, on each DLT audit entry |
| `fraud_transactions_pending` | — | `BacklogMetrics` gauge, refreshed every 30s |
| `fraud_outbox_events` | `status` | `BacklogMetrics` gauge, refreshed every 30s |

The counters are registered at zero on startup, so `rate()` / `increase()` panels count the very
first event. Error-status and skipped rule series are still created on first use.

## Testing

`fraud-engine-service` has unit tests (JUnit 5, Mockito, AssertJ — no Spring context, database
or Kafka), covering the rule maths, the risk score, rule handling and completion, the outbox
writer and relay, the metrics aspect and backlog gauges, and the REST controllers' status codes
and validation.

Run them with Maven, or without a local JDK:

```bash
docker run --rm -v "${PWD}/fraud-engine-service:/build" -w /build maven:3.9-eclipse-temurin-25 mvn -B test
```

Not covered yet, and better suited to Testcontainers integration tests: the repository SQL
(row lock, stats and area queries), the completion race under real concurrency, and the Kafka
listener wiring. End-to-end behaviour has been verified manually by pushing transactions onto
Kafka, checking `evaluated_transactions` / `rule_hits` / outbox rows and the metrics, and
exercising the API through the gateway with tokens for both roles (including `FraudTeam` being
rejected with `403` from the override endpoint).

No CI pipeline is wired up yet.
