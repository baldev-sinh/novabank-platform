# Alerts and runbooks

The rules are in [`ops/prometheus/rules/novabank-alerts.yml`](../../ops/prometheus/rules/novabank-alerts.yml)
and are unit-tested in CI (`ops/prometheus/tests`). Alertmanager
([`ops/alertmanager/alertmanager.yml`](../../ops/alertmanager/alertmanager.yml)) groups them by
alert and service and routes them by severity. Locally every alert is emailed to Mailpit
(http://localhost:8025). Dashboards: Grafana at http://localhost:3000, folder *NovaBank*.

| Severity | Means | Repeats |
|---|---|---|
| `critical` | Money or data at risk, or a service is down: act now | every hour |
| `warning` | Degraded, or something needs a look today | every 4 hours |

`ServiceDown` suppresses the other alerts of the same service: a stopped service also has
errors, lag and a backlog, and paging for all of them hides the cause.

---

## ServiceDown

Prometheus can't scrape a service's management port for over a minute.
**Check** `docker compose ps` / the pod status and the service's last log lines. A failing
readiness probe (`/readyz`) usually names the dependency (database, Kafka).
**Then** restart it once the dependency is back. Its consumers resume from their committed offsets
and its outbox sends whatever piled up, so nothing is lost while it is down.

## HighServerErrorRate

More than 5% of a service's requests have answered 5xx for 5 minutes (services under 0.1 requests/s are ignored).
**Check** *NovaBank · Services*, then *Slowest endpoints* and the logs. Every 5xx carries a
`traceId` (RFC 9457 body) to look up in Jaeger.
**Then** if it started with a deploy, roll back. If a dependency is failing, see its own alert.

## SlowRequests

The API's p95 latency has been above 1 s for 10 minutes.
**Check** *Slowest endpoints (p95)* and *Database connections*: waiting connections point at the
pool or the database, and one slow endpoint points at that endpoint's query.
**Then** look at the slow traces in Jaeger and the query plans.

## DatabasePoolExhausted

Requests have been waiting for a HikariCP connection for 2 minutes.
**Check** for long transactions holding connections. The deposit check holds one for up to its
3 s of timeouts, and an open circuit fails fast. Also check database health.
**Then** fix the slow query or transaction. Raise `*_DB_POOL_SIZE` only if the database has room.

## PaymentsNeedAttention

The payment saga asked a step `max-attempts` times without an answer and flagged the payment.
Money may be held, or posted but not yet confirmed.
**Check** `GET /api/v1/admin/payments/{id}` (OPERATIONS) and the ledger for that `paymentId`. The
saga never fails a payment on its own once the ledger outcome is unknown.
**Then** bring back the service that doesn't answer (see its alerts). The sweeper keeps asking, and
the flag clears when the payment moves on.

## PaymentStuck

A payment has been in flight for more than 10 minutes; they normally finish in seconds.
**Check** which step it is in (`status`) and the alerts of the service that step waits for
(account for holds, ledger for postings). Also check `OutboxBacklog` and `ConsumerLag`.

## ProjectionDrift

The ledger's balance snapshot doesn't match the account service's balances for some accounts.
The ledger is the source of truth.
**Check** the account service's WARN log for the drifted account ids, then compare
`GET /api/v1/ledger/accounts/{id}` with the account's balance.
**Then** find the event that wasn't applied (`DeadLetteredRecords` on `ledger.events.v1`), fix it
and replay it. Never edit balances by hand.

## AuditChainBroken

The hourly check of the audit log found an entry that was changed, removed or inserted outside the
audit service. **Treat as a security incident.**
**Check** `GET /api/v1/audit/verify` (COMPLIANCE_OFFICER or ADMIN) for `brokenAtSeq` and `problem`,
and the database's audit logs for who connected. Don't "repair" the chain: preserve the database
for investigation.

## OutboxBacklog

A service's oldest unpublished event is more than 5 minutes old, so other services aren't hearing
about its changes. Payments and account activation stall.
**Check** Kafka health and the service's log ("Publishing outbox message … failed").
**Then** once Kafka is reachable the relay catches up in order by itself. Nothing is lost: the
events are in the service's database.

## DeadLetteredRecords

A service gave up on an event after its retries, or at once if the event couldn't be read, and
put it in its own dead-letter topic `<topic>.<group>-dlt`.
**Check** the record's `kafka_dlt-exception-*` headers for the reason.
**Then** fix the cause (a bug, or a contract change upstream), deploy, and replay the record to the
source topic. Consumers are idempotent, so replaying is safe. The topic belongs to one service, so a
replay reaches only that one.

## ConsumerLag

A consumer has been more than 1000 records behind for 10 minutes.
**Check** the consumer's errors and retries (a poison record retried with backoff blocks its
partition until it is dead-lettered), and whether it is just slow (*Database connections*).

## CircuitBreakerOpen

The payment service stopped calling the account service after repeated failures. Deposits are
refused with 503 (fail closed) until the account service answers again.
**Check** the account service's alerts and health. The circuit tries again after 30 s by itself.

## NotificationsFailing

Emails were given up on after 10 attempts (about 4 hours of retries).
**Check** the mail relay and the notification service's log (ids only, never addresses). Each
FAILED row in `notifications` has `last_error`.
**Then** once the relay works, set the rows back to `PENDING` with `attempts = 0` to resend them.
