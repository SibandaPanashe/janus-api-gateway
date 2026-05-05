# ADR-004: Hash-Chained Audit Log Over Standard Append Log

## Status
Proposed

## Context
Janus handles API access control and billing decisions. In regulated
environments — and for any platform where trust is a product feature —
these decisions must be auditable. Specifically:

- Who was allowed through, and when
- Who was blocked, and why
- When rules changed, and who changed them
- When a client's plan was modified

A standard audit log (append-only table in PostgreSQL) satisfies the
basic requirement. However, a standard log has a critical weakness:
**a sufficiently privileged actor can modify or delete records without
detection.** A database administrator, a compromised service account,
or a malicious insider can alter the historical record.

For a billing and access control system, this is unacceptable. If a
client disputes a charge, the audit record must be provably unmodified.

## Decision
Implement a hash-chained audit log where each record contains the
cryptographic hash of itself combined with the hash of the previous
record, using SHA-256.

The structure of each audit record:

| Field            | Description                                          |
|------------------|------------------------------------------------------|
| id               | Auto-incremented primary key                         |
| timestamp        | UTC timestamp to millisecond precision               |
| actor            | Authenticated identity that triggered the event      |
| action           | The event type (ACCESS_ALLOWED, ACCESS_DENIED, etc.) |
| entity_id        | The API key or client ID involved                    |
| payload          | JSON snapshot of the relevant state                  |
| previous_hash    | SHA-256 hash of the previous record's content        |
| current_hash     | SHA-256(previous_hash + this record's content)       |

A `/admin/audit/verify` endpoint walks the entire chain and confirms
that no record has been modified since it was written. Any gap,
deletion, or modification breaks the hash chain and is immediately
flagged.

This is implemented as a Spring AOP `@AfterReturning` aspect on all
methods annotated with `@Auditable`, ensuring audit records are written
consistently without relying on individual developers remembering to
call an audit service manually.

## Why Not a Standard Append-Only Table?
An append-only table with database-level restrictions prevents
accidental modification but does not prevent deliberate modification
by a privileged actor. Hash chaining provides **cryptographic proof**
that records are unmodified — not just an operational guarantee.

The structural principle is identical to a blockchain: each block
(audit record) contains the hash of the previous block, making the
chain tamper-evident without requiring a distributed consensus
mechanism. A single-node hash chain is sufficient for audit purposes.

## Why Spring AOP for Enforcement?
If audit logging is the responsibility of individual developers —
calling `auditService.log(...)` manually in each method — it will
eventually be forgotten. One missed call on one sensitive method
creates an audit gap that may not be discovered until it matters most.

Spring AOP moves the responsibility from individual developers to the
framework. Any method annotated `@Auditable` is automatically covered.
The audit logic cannot be accidentally skipped.

## Consequences
- Tamper-evidence is cryptographically provable, not just operational ✅
- AOP enforcement eliminates audit gaps from developer oversight ✅
- Satisfies audit requirements in regulated environments ✅
- Chain verification is O(n) — verifying the full log scans all
  records. Mitigated by periodic snapshot checkpoints ⚠️
- Record insertion is slightly slower due to hash computation.
  SHA-256 on a small JSON payload is ~0.1ms — acceptable ⚠️
- Records cannot be deleted for data retention compliance without
  breaking the chain. Archival strategy required for old records ⚠️

## References
- SHA-256 cryptographic hash function
- Spring AOP documentation: https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop
- Principles of tamper-evident logging in financial systems
