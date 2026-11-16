# ADR-008: Saga Transport via Kafka Commands and Replies

## Status

Accepted (2026-11-16)

## Context

ADR-002 makes the payment service the saga orchestrator. It must tell the account service to hold
or release funds and the ledger service to post transactions, and learn the outcome.

Options considered:

1. **Kafka commands and reply events**, all written through each service's transactional outbox.
2. **Synchronous REST** calls from the payment service, with timeouts, retries and circuit
   breakers.

## Decision

Option 1. The payment service writes `HoldFunds`, `ReleaseHold` and `PostLedgerTransaction`
commands to topics owned by the receiving services (`account.commands.v1`, `ledger.commands.v1`).
The receivers reply with events on their own topics, keyed by payment id.

The receivers give **one final answer per payment id** (one hold per payment, one ledger
transaction per payment, refusals stored), so the orchestrator can re-send a command after a
timeout without risk.

## Consequences

### Benefits

* A service being down only delays payments; nothing is lost, because commands wait in Kafka.
* No "did it happen?" ambiguity after a timeout: asking again returns the same answer.
* Reuses the outbox, idempotent consumers and dead-letter handling that every service already has.

### Trade-offs

* The orchestrator needs persisted saga state, row locks for concurrent replies, and a sweeper
  for timeouts.
* Replies arrive on two topics, so their order is not guaranteed; the state machine must accept
  out-of-order replies.
* Eventual consistency: the API answers 202 and the client polls the payment's status.
