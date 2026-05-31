# ADR-0001 — In-Memory Temporal Ledger with Append-Only Persistence

**Status:** Accepted  
**Date:** 2026-05-29  
**Author(s):** irenelau-gds

---

## Context

A ledger application is needed to record financial transactions (deposits and withdrawals) against named accounts. The initial version is a learning and demonstration project, so operational concerns such as database provisioning, infrastructure cost, and deployment complexity are secondary to getting a working, well-structured service up quickly.

The following design questions needed answers before implementation could begin:

1. Where and how should data be persisted?
2. How should the data model handle historical queries (balance as of a past point in time)?
3. How should concurrent writes be made safe without introducing a full database?
4. How should deposits and withdrawals be represented — as separate transaction types, or a single model?

---

## Assumptions

_List the assumptions that underpin this decision. If an assumption later proves false, the decision should be revisited._

- [ ] The application runs as a **single JVM instance**. There is no requirement for horizontal scaling or cross-node data sharing at this stage.
- [ ] **Data durability is not required.** All in-memory state may be lost on restart; this is acceptable for a demo/learning context.
- [ ] **Transaction volume is low.** Linear scans over `CopyOnWriteArrayList` are acceptable; indexing and query optimisation are not needed yet.
- [ ] **Account schema is stable.** Accounts are created once and not amended (currency and type do not change after creation). This assumption has already been partially revisited in Session 2 with the addition of `lowerBound`.
- [x] **A single application lock is sufficient for correctness.** Per-account locking would improve throughput but adds complexity; a global `ReentrantLock` is adequate at current load expectations. — _Assumption retired: superseded by ADR-0002, which introduces per-account locking._
- [ ] **No authentication or authorisation is required** for the initial version.
- [ ] **Currencies are validated by exact match** against the account's configured currency. No currency conversion is in scope.

---

## Decision

We will implement the ledger as a **Spring Boot REST application with a fully in-memory, append-only persistence layer** built on `CopyOnWriteArrayList`, using a **bitemporal data model** (valid-time tracking via `validFromUtc` / `validToUtc`) for both `Account` and `Transaction` records. Thread safety for balance-check + write sequences will be provided by a `ReentrantLock` in the transaction write service. Deposits and withdrawals will be unified as a single `Transaction` model, differentiated by the sign of the `amount` field.

---

## Rationale

### In-memory persistence
Eliminates infrastructure setup (no database, no migrations, no connection pooling). Appropriate for a demo scope. `CopyOnWriteArrayList` provides thread-safe iteration and atomic single-element appends at no locking cost for reads.

### Temporal (bitemporal) data model
Every record carries `validFromUtc` and `validToUtc` (`java.time.Instant`). Active records have `validToUtc = Instant.MAX`. This enables:
- Point-in-time balance queries without storing derived snapshots.
- Non-destructive history — nothing is ever mutated or deleted; corrections append new records.
- A foundation for future transaction reversal (close the existing record, append a compensating entry).

Alternative considered: storing only the current state and a separate audit log. Rejected because it splits query logic across two stores and makes as-of queries more complex.

### `ReentrantLock` for write serialisation
`CopyOnWriteArrayList.add()` is itself atomic, but the balance-check → write sequence is a compound operation that must be atomic to prevent double-spend (two concurrent withdrawals both reading a balance of £50 and both deciding they can proceed with a £50 withdrawal). A `ReentrantLock` around the entire check-then-write block eliminates this race.

Alternative considered: `synchronized` on the repository. Rejected in favour of `ReentrantLock` for explicit lock visibility and easier future migration to per-account striped locking.

Alternative considered: optimistic concurrency with a version counter. Rejected as over-engineered for the current scale assumption.

### Unified `amount` field (positive = deposit, negative = withdrawal)
A single `applyTransaction` method handles both operations via the sign of `amount`. This keeps the model simple, the service method surface small, and the balance calculation trivial (`SUM(amount)`). The REST controller normalises the sign before delegating (deposits pass amount as-is, withdrawals negate it) so the API consumer always supplies a positive number.

---

## Consequences

### Positive
- Zero infrastructure dependencies — the application starts with `mvn spring-boot:run` and no setup.
- Full point-in-time query support from day one without additional complexity.
- Simple, auditable data model: the store is an immutable log of facts.
- Concurrency correctness guaranteed by the lock without needing a database transaction.

### Negative / Trade-offs
- **Not durable.** All data is lost on restart.
- **Does not scale horizontally.** The in-memory store and single JVM lock are incompatible with multi-instance deployment.
- **Linear read performance.** Balance queries and history queries scan the full transaction list. Acceptable at low volume; will degrade at scale.
- **Single global lock** serialises all write operations across all accounts, limiting write throughput under concurrency.

### Risks
- The assumption of single-JVM deployment may be revisited if the project grows. Migrating to a real database at that point will require adding a persistence layer (JPA/JDBC) and replacing the in-memory repositories — a significant but well-scoped change.
- If `CopyOnWriteArrayList` grows very large, the copy-on-write semantics of `add()` will cause GC pressure. A `ConcurrentLinkedQueue` or a proper database should be considered before that point.

---

## Notes / Follow-up

- The assumption of a stable account schema was already partially revisited in Session 2 when `lowerBound` was added to `Account`. Future changes to account structure may warrant a dedicated ADR.
- Per-account striped locking is noted as a future improvement in code comments within `TransactionManagerService`.
- Related session summaries: `docs/Session1_Summary.md`, `docs/Session2_Summary.md`.
- Related ADRs: ADR-0002
- Supersedes: —
- Superseded by: — (partially: locking strategy superseded by ADR-0002)
