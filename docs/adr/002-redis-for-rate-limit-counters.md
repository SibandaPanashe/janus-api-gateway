# ADR-002: Redis Sliding Windows for Rate Limit Counters Over Database Writes

## Status
**Accepted** (Implemented May 2026)

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

## Decision

Use Redis **sorted sets** (not simple INCR) as the primary store for
per-second rate limit counters. The algorithm is a true sliding window
with millisecond precision:
