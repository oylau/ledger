# ADR-0002 — Per-Account Locking via ConcurrentHashMap

**Status:** Accepted  
**Date:** 2026-05-30  
**Author(s):** irenelau-gds

---

## Context

ADR-0001 introduced a single global `ReentrantLock` in `TransactionManagerService` to serialise the balance-check + write sequence and prevent double-spend race conditions. The assumption at the time was that a single lock was sufficient for the expected load.

That assumption has been revisited. With a global lock, any two concurrent transactions — even on completely unrelated accounts — must wait for each other. This is unnecessarily conservative: correctness only requires that two transactions on the **same** account are serialised; transactions on **different** accounts are entirely independent and should be able to proceed in parallel.

---

## Assumptions

- [ ] The set of active accounts fits comfortably in memory alongside the lock objects. One `ReentrantLock` instance per account is negligible overhead at current scale.
- [ ] Account numbers are stable identifiers. A lock acquired by account number remains valid for the lifetime of the application because accounts are never deleted or renamed in the current model.
- [ ] Lock entries in the map are never explicitly removed. Accumulation of lock objects over the application lifetime is acceptable given the assumption above.
- [ ] The application still runs as a single JVM instance. Cross-node distributed locking is out of scope.

---

## Decision

We will replace the single `ReentrantLock` in `TransactionManagerService` with a `ConcurrentHashMap<String, ReentrantLock>` keyed by account number. A lock for a given account is created lazily via `computeIfAbsent` on first use and reused thereafter.

```java
private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

// Inside applyTransaction:
ReentrantLock lock = accountLocks.computeIfAbsent(accountNumber, k -> new ReentrantLock());
lock.lock();
try {
    // balance check + write (unchanged)
} finally {
    lock.unlock();
}
```

---

## Rationale

### Why `ConcurrentHashMap.computeIfAbsent`?
`computeIfAbsent` is atomic on `ConcurrentHashMap`: two threads racing to create a lock for the same new account are guaranteed to receive the same `ReentrantLock` instance. No additional synchronisation is needed around the map itself.

### Why not Guava `Striped<Lock>`?
`Striped` pre-allocates a fixed number of lock buckets and maps account numbers to buckets via hashing, accepting a small probability of unrelated accounts sharing a bucket (hash collision). It is a good choice when the number of active keys is unbounded and memory is a concern. In this application the number of accounts is expected to stay small, so a 1:1 map is simpler and gives strictly better isolation with no false contention.

### Why not `synchronized` blocks?
`synchronized` on an arbitrary object would work but requires careful management of the monitor object's lifecycle and is less explicit than `ReentrantLock`. `ReentrantLock` also supports `tryLock` and timed acquisition, which may be useful in future.

### Why not optimistic concurrency / compare-and-swap?
Optimistic concurrency requires a retry loop and a version mechanism on the transaction store. This adds complexity without a clear benefit at the current scale.

---

## Consequences

### Positive
- Transactions on different accounts now proceed concurrently with zero contention between them.
- Correctness guarantees are unchanged: same-account transactions are still fully serialised.
- No change to the public API, repository layer, or test structure — the change is entirely internal to `TransactionManagerService`.

### Negative / Trade-offs
- One `ReentrantLock` object is allocated per account and held in the map indefinitely. At very large account counts this could become a memory concern, though it is negligible in the current context.
- Slightly more complex than a single field lock; the map and `computeIfAbsent` call must be understood by future maintainers.

### Risks
- If account numbers were ever recycled (an account deleted and a new account created with the same number), the new account would inherit the old account's lock. This is harmless in the current model where accounts are never deleted, but would need revisiting if soft-delete or account recycling is introduced.

---

## Notes / Follow-up

- If the number of accounts grows very large, consider replacing the map with `Striped<ReentrantLock>` from Guava to bound memory usage at the cost of occasional false contention.
- If the application ever moves to a multi-node deployment, per-JVM locking will not provide cross-node safety. A distributed lock (e.g. Redis `SETNX`, a database advisory lock, or a ZooKeeper recipe) would be required.
- A new concurrency test `concurrentTransactions_onDifferentAccounts_doNotBlockEachOther` was added to `TransactionServiceTest` to verify that two accounts can be written to simultaneously without interference.
- Related ADRs: ADR-0001
- Supersedes: the locking strategy section of ADR-0001
- Superseded by: —
