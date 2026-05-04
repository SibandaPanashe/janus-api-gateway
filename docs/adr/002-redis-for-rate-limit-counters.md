# ADR-002: Redis for Rate Limit Counters Over Database Writes

## Status
Proposed

## Context
Every request that passes through Janus must increment a usage counter
for the client that sent it. This counter is used for:
- Real-time rate limit enforcement (is this client over their limit?)
- Billing calculation (how many requests did this client make this month?)
- Dashboard display (what is this client's current usage?)

The naive implementation writes a row to PostgreSQL on every request.
Under load — thousands of requests per second across many clients —
this creates a write bottleneck on the database, adds latency to every
request, and exhausts database connections.

Two alternatives were evaluated:
1. PostgreSQL with a write-optimised counter table
2. Redis with atomic INCR commands

## Decision
Use Redis as the primary store for all rate limit counters and
real-time usage data, using the following key pattern:

    API_REQUEST_COUNT:{clientId}:{YYYY-MM-DD-HH-mm}

Each request executes INCR on this key. Redis INCR is atomic —
meaning concurrent requests from the same client cannot produce an
incorrect count even without application-level locking.

Keys are given a TTL of 90 days to prevent unbounded memory growth.
A separate scheduled job aggregates daily totals into PostgreSQL for
long-term billing records and reporting.

## Why Not PostgreSQL on the Hot Path?
A PostgreSQL UPDATE counter = counter + 1 requires:
- A network round trip to the database
- Row-level locking to prevent race conditions
- A write-ahead log entry
- Potential lock contention under concurrent requests

A Redis INCR requires:
- A network round trip to Redis (typically 0.1-0.5ms vs 1-5ms for PG)
- No locking — Redis is single-threaded per command, atomicity is free
- No disk I/O on the hot path

At 1,000 requests per second, this difference is the boundary between
a system that scales and one that does not.

## Consequences
- Sub-millisecond counter increments on the hot path ✅
- Atomic operations eliminate race conditions without application locks ✅
- Redis memory is finite — TTL strategy is required ⚠️
- Redis is now a critical dependency — a Redis outage affects rate
  limiting. Mitigation: Redis Sentinel or Cluster for HA ⚠️
- Long-term billing data requires a separate aggregation job,
  adding operational complexity ⚠️

## References
- Redis INCR documentation: https://redis.io/commands/incr/
- Token Bucket algorithm for rate limiting
