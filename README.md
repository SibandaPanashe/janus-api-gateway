# Janus — Intelligent API Gateway & Monetization Engine

> *"Every API needs a guardian. Janus is yours."*

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Status](https://img.shields.io/badge/Status-In%20Development-blue.svg)]()

---

## What Is Janus?

Janus is a high-performance, transparent, and ruthlessly configurable API gateway built for API-first companies that need more than what off-the-shelf solutions offer.

In plain terms: when developers or businesses consume your API, Janus stands between them and your system. It checks who they are, enforces what they are allowed to do based on their subscription plan, counts every request for billing purposes, and protects your infrastructure from abuse — all without your application knowing or caring.

The key differentiator: **the business logic (rate limits, billing rules, access tiers) lives in a Drools rules engine, not in hard-coded application logic.** This means rules can be updated, tested, and promoted to production without a redeployment.

---

## The Problem It Solves

Building an API is the easy part. Monetizing it and protecting it at scale is where most teams struggle.

Without a purpose-built gateway you face these problems:

- **Rate limiting is brittle** — hard-coded limits that require a deployment to change
- **Billing logic bleeds into application code** — making both harder to maintain
- **Abuse is invisible until it's too late** — no real-time visibility into who is hammering your system
- **Upgrades are manual** — customers email you to increase their limits; you change a config file by hand

Janus solves all four. The rules engine makes limits dynamic. The Redis-backed usage tracker makes billing automatic. The dashboard gives customers self-service visibility. The Nginx edge layer catches abuse before it reaches your application.

---

## Architecture Overview

```
                        ┌─────────────────────────────────────────┐
                        │           CLIENT / API CONSUMER          │
                        └──────────────────┬──────────────────────┘
                                           │ HTTPS Request
                                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        TIER 1: NGINX EDGE                            │
│                                                                      │
│   ┌─────────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│   │  Connection     │  │  Rate Limiting   │  │  Micro-Cache      │  │
│   │  Limiting       │  │  (Burst Control) │  │  (Public GETs)    │  │
│   │  limit_conn     │  │  limit_req_zone  │  │  proxy_cache      │  │
│   └─────────────────┘  └──────────────────┘  └───────────────────┘  │
└──────────────────────────────────────┬───────────────────────────────┘
                                       │ Proxied Request
                                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     TIER 2: JANUS CORE ENGINE                        │
│                        (Spring Boot)                                 │
│                                                                      │
│   ┌─────────────────┐        ┌──────────────────────────────────┐   │
│   │  API Key        │───────▶│     Redis                        │   │
│   │  Resolution     │        │   - Client profiles              │   │
│   └─────────────────┘        │   - Request counters             │   │
│                              │   - Idempotency keys             │   │
│   ┌─────────────────┐        │   - Usage pub/sub                │   │
│   │  Drools Rules   │◀───────└──────────────────────────────────┘   │
│   │  Engine         │                                                │
│   │  - Plan limits  │        ┌──────────────────────────────────┐   │
│   │  - Billing      │───────▶│     PostgreSQL                   │   │
│   │  - Surcharges   │        │   - Audit log (hash-chained)     │   │
│   └─────────────────┘        │   - Client accounts              │   │
│                              │   - Rule definitions             │   │
│   ┌─────────────────┐        └──────────────────────────────────┘   │
│   │  Spring AOP     │                                                │
│   │  - Audit log    │                                                │
│   │  - Idempotency  │                                                │
│   └─────────────────┘                                                │
└──────────────────────────────────────┬───────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    TIER 3: ADMIN DASHBOARD                           │
│                      (React + TypeScript)                            │
│                                                                      │
│   Real-time usage graph  │  Plan management  │  API key rotation    │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Core Features

### Dynamic Rule-Based Rate Limiting
Rate limits are not hard-coded. They are defined in Drools rule files (`.drl`) stored in the database and hot-reloaded at runtime. A non-technical administrator can change the rate limit for the "free" tier from 10 req/sec to 15 req/sec without touching the codebase or triggering a deployment.

### Tiered Access Control
Clients are assigned a plan — `free`, `starter`, `pro`, `enterprise`, or `pay-as-you-go`. Drools evaluates the client's plan and request profile on every request and makes the allow/block/throttle decision in milliseconds.

### Redis-Backed Usage Tracking
Every request increments a counter in Redis using the pattern `API_REQUEST_COUNT:{clientId}:{minute}`. This enables per-minute, per-hour, and per-day usage visibility with zero database writes on the hot path.

### Tamper-Evident Audit Logging
Every access decision, rule change, and administrative action is written to a hash-chained audit log. Each record stores the SHA-256 hash of itself combined with the previous record, making tampering detectable. This satisfies audit requirements in regulated environments.

### Idempotency Protection
State-changing requests accept an `Idempotency-Key` header. Janus stores the result of the first execution in Redis. Duplicate requests within the TTL window return the cached result without re-execution. This prevents double-billing and double-processing under retry conditions.

### Real-Time Dashboard
API consumers see their usage in real time via a React dashboard. Usage data is pushed from the Janus engine via Redis Pub/Sub and WebSocket, not polled. The dashboard shows current request count, remaining quota, plan tier, and billing estimate.

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Edge / Reverse Proxy | Nginx | Connection limiting, burst control, micro-caching before requests hit Java |
| Core Engine | Java 17, Spring Boot 3 | Enterprise-grade, battle-tested in financial services |
| Rules Engine | Drools | Separates rate-of-change of business rules from application code |
| Caching / Counters | Redis | Sub-millisecond reads for API key lookup and request counting |
| Persistence | PostgreSQL | Audit log, client accounts, rule definitions |
| Cross-Cutting Concerns | Spring AOP | Audit logging and idempotency enforcement without polluting business logic |
| Security | Spring Security | API key authentication, endpoint protection |
| Dashboard | React, TypeScript | Type-safe, real-time client-facing interface |

---

## Theoretical Foundation

This project is not just engineering instinct. The design decisions are grounded in computer science research:

**Rate Limiting Algorithm — Token Bucket**
The rate limiting implementation follows the Token Bucket algorithm, where each client has a conceptual bucket that refills at a fixed rate. Requests consume tokens. An empty bucket means the request is rejected. This prevents the boundary exploit present in naive fixed-window rate limiters, where a client can double their effective rate by straddling a window boundary.

**Load Shedding at the Edge**
The Nginx configuration is designed around the principle of load shedding: reject work at the earliest possible layer rather than letting it propagate inward. A request blocked at Nginx costs microseconds. A request that reaches the database and fails costs milliseconds and holds a connection. The multi-layer Nginx config (connection limit → rate limit → cache) implements this principle explicitly.

**Tamper-Evident Logging — Hash Chaining**
The audit log uses the same structural principle as a blockchain: each record contains the hash of the previous record. This means deleting or modifying any record breaks the chain and is immediately detectable by the verification endpoint. This design is informed by cryptographic integrity principles applied to append-only audit systems.

**Distributed Locking for Idempotency**
The idempotency key implementation uses Redis `SET NX` (set if not exists) as a distributed lock to handle the in-flight race condition: two identical requests arriving simultaneously before either has completed. Only one acquires the lock; the other waits or fails fast.

---

## Subscription Tiers

| Plan | Requests/sec | Requests/month | Price |
|---|---|---|---|
| Free | 10 | 100,000 | $0 |
| Starter | 50 | 500,000 | $29/mo |
| Pro | 200 | 5,000,000 | $99/mo |
| Enterprise | Custom | Unlimited | Contact |
| Pay-as-you-go | 100 | Metered | $0.01/1k req |

All limits are enforced by the Drools rules engine and can be adjusted without redeployment.

---

## Build Roadmap

- [x] Project setup and architecture design
- [ ] **Week 1–2:** Nginx edge configuration with multi-layer rate limiting
- [ ] **Week 3–4:** Spring Boot core, Redis API key resolution, Drools integration
- [ ] **Week 5–6:** Monetization rules, usage counters, idempotency layer
- [ ] **Week 7–8:** React dashboard, WebSocket usage streaming, load testing
- [ ] **Post-MVP:** Rule hot-reload UI, multi-region Redis, Prometheus metrics endpoint

---

## Architecture Decision Records

Key engineering decisions are documented in [`/docs/adr`](/docs/adr). These explain not just *what* was built but *why*, including the trade-offs considered and rejected.

| ADR | Decision |
|---|---|
| [ADR-001](/docs/adr/001-drools-over-hardcoded-rules.md) | Why Drools instead of application-level if-statements |
| [ADR-002](/docs/adr/002-redis-for-rate-limit-counters.md) | Why Redis counters instead of database writes on the hot path |
| [ADR-003](/docs/adr/003-nginx-edge-layer.md) | Why Nginx handles the first line of rate limiting, not Spring |
| [ADR-004](/docs/adr/004-hash-chained-audit-log.md) | Why the audit log uses hash chaining instead of a standard append log |

---

## Engineering Context

This project was designed and built during an industrial attachment at an IT consultancy operating in a regulated banking environment. The requirements that shaped its architecture — idempotency, tamper-evident audit trails, dynamic business rules, structured logging — are not academic exercises. They are production constraints encountered daily in financial services engineering.

The design reflects the realities of building software where correctness, auditability, and operational safety matter more than development speed.

---

## Getting Started

> ⚠️ **Under active development.** Setup instructions will be published at Week 2 of the build roadmap.

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Author

Built with intent. Questions, feedback, and contributions welcome.
