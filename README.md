# Janus — Intelligent API Gateway & Monetization Engine

> *"Every API needs a guardian. Janus is yours."*

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![Status](https://img.shields.io/badge/Status-Production%20Ready-green.svg)]()

---

# What Is Janus?

Janus is a high-performance, transparent, and ruthlessly configurable API gateway built for API-first companies that need more than what off-the-shelf solutions offer.

In plain terms: when developers or businesses consume your API, Janus stands between them and your system. It checks who they are, enforces what they are allowed to do based on their subscription plan, counts every request for billing purposes, and protects your infrastructure from abuse — all without your application knowing or caring.

The key differentiator: **the business logic (rate limits, billing rules, access tiers) lives in a Drools rules engine, not in hard-coded application logic.** This means rules can be updated, tested, and promoted to production without a redeployment.

---

# The Problem It Solves

Building an API is the easy part. Monetizing it and protecting it at scale is where most teams struggle.

Without a purpose-built gateway you face these problems:

- **Rate limiting is brittle** — hard-coded limits that require a deployment to change
- **Billing logic bleeds into application code** — making both harder to maintain
- **Abuse is invisible until it's too late** — no real-time visibility into who is hammering your system
- **Upgrades are manual** — customers email you to increase their limits; you change a config file by hand

Janus solves all four.

The rules engine makes limits dynamic. The Redis-backed usage tracker makes billing automatic. The dashboard gives customers self-service visibility. The Nginx edge layer catches abuse before it reaches your application.

---

# Architecture Overview

```text
                          INTERNET
                             |
                             v

+----------------------------------------------------------+
|                    TIER 1: NGINX EDGE                    |
|                                                          |
|   +--------------+  +--------------+  +---------------+  |
|   | Connection   |  | Rate Limit   |  | Micro-Cache   |  |
|   | 20 conn/IP   |  | 30 req/s     |  | 5s GET TTL    |  |
|   +--------------+  +--------------+  +---------------+  |
|                                                          |
|   Load Shedding + Custom JSON Error Pages                |
+--------------------------+-------------------------------+
                           |
                           v

+----------------------------------------------------------+
|                TIER 2: JANUS CORE ENGINE                 |
|                  (Spring Boot 3.4)                       |
|                                                          |
|   +--------------+     +--------------+  +-------------+ |
|   | API Key      |---->| Redis        |  | PostgreSQL  | |
|   | Resolution   |     | - Counters   |  | - Clients   | |
|   +--------------+     | - Cache      |  | - Rules     | |
|                        +--------------+  | - Audit Log | |
|   +--------------+                       +-------------+ |
|   | Drools Rules |                                       |
|   | Engine       |     +--------------+                  |
|   | - Limits     |     | JWT Auth     |                  |
|   | - Billing    |     | - HS384      |                  |
|   | - Surcharges |     | - Refresh    |                  |
|   +--------------+     +--------------+                  |
|                                                          |
|   Trace IDs | Structured Logging | Event Audit Trail     |
+----------------------------------------------------------+
                           |
                           v

+----------------------------------------------------------+
|                TIER 3: ADMIN DASHBOARD                   |
|                (React + TypeScript)                      |
|                                                          |
|   Real-time Usage | Plan Management | API Key Rotation   |
|   (WebSocket + Redis Pub/Sub)                            |
+----------------------------------------------------------+
```

---

# Core Features

## SHA-256 API Key Hashing

API keys are generated with a `sk-` prefix and 32 bytes of secure random entropy. Only the SHA-256 hash is stored — never the raw key itself.

- Raw key returned exactly once at creation time
- Redis stores hot cache entries
- PostgreSQL remains source of truth
- Automatic cache repopulation on Redis miss

---

## JWT Authentication (HS384)

Exchange an API key for a signed JWT access token.

### Token Configuration

| Token | TTL |
|---|---|
| Access Token | 1 hour |
| Refresh Token | 30 days |

### Security Design

- Stateless JWT validation
- No Redis lookup required for verification
- HS384 signature algorithm
- Refresh tokens embed key hash for stateless rotation

---

## Dynamic Rule-Based Rate Limiting

Rate limits are defined in Drools `.drl` files stored in PostgreSQL and hot-reloaded at runtime.

This means an administrator can:

- Increase free tier limits
- Add enterprise-specific billing logic
- Introduce surge pricing
- Block abusive tenants

…without restarting the application or deploying new code.

### Runtime Rule Operations

```bash
POST /admin/rules
PATCH /admin/rules/{id}/toggle
POST /admin/rules/reload
```

Rules compile into a fresh `KieContainer` and are atomically swapped with zero downtime.

---

## Redis Sliding Window Counters

Janus implements true sliding-window rate limiting using Redis sorted sets.

Algorithm flow:

1. Request timestamp added to sorted set
2. Entries older than 1 second pruned
3. Remaining count becomes current rate
4. Rule engine decides allow/block

Benefits:

- Millisecond precision
- No fixed-window boundary exploit
- Same architectural approach used by Kong and AWS API Gateway

---

## PostgreSQL Persistent Storage

Persistent entities include:

- Clients
- Rules
- Audit events
- API key metadata

### Database Strategy

| Component | Role |
|---|---|
| Redis | Fast operational cache |
| PostgreSQL | Durable source of truth |
| Flyway | Schema migration management |

On Redis cache miss, Janus transparently falls back to PostgreSQL and repopulates Redis automatically.

---

## Structured Audit Logging

Every gateway decision is logged with:

- Trace ID
- Client ID
- Subscription plan
- Request count
- Response code
- Rule evaluation outcome

### Logging Stack

- Logback
- Logstash JSON encoder
- Structured JSON logs
- Correlated trace propagation

Development mode uses human-readable console logging with trace identifiers.

---

## Nginx Edge Protection (3-Layer Defense)

### Layer 1 — Connection Limiting

- 20 concurrent connections per IP

### Layer 2 — Static Rate Limiting

- 30 req/sec
- Burst handling enabled

### Layer 3 — Micro-Caching

- 5-second TTL
- Public GET endpoint optimization

### Benchmark Result

> 91 of 100 abusive requests blocked at the Nginx edge before reaching Java.

---

## Hot-Reload Rules Engine

Rules can be created, enabled, disabled, or reloaded while the system is live.

### Features

- Runtime rule creation
- Dynamic enable/disable
- Atomic rule swaps
- Zero-downtime reloads
- PostgreSQL-backed rule persistence

---

## Dockerized Deployment

Entire platform deploys with one command:

```bash
docker-compose up -d --build
```

### Stack Components

- Redis
- PostgreSQL
- Janus Core
- Nginx

### Infrastructure Features

- Multi-stage Docker builds
- Alpine JRE images
- Health checks on all services
- Containerized local development

---

## Secrets Management (12-Factor)

All secrets are externalized via environment variables.

### Configuration Design

- `.env.example` committed
- `.env` ignored by Git
- `${VAR:default}` Spring configuration pattern
- Docker-compatible configuration

---

# Tech Stack

| Layer | Technology | Version | Why |
|---|---|---|---|
| Edge Proxy | Nginx | 1.25-alpine | Event-driven abuse prevention |
| Core Engine | Java + Spring Boot | 17 + 3.4.3 | Enterprise-grade runtime |
| Rules Engine | Drools | 10.0.0 | Dynamic business rule execution |
| Cache / Counter | Redis | 7-alpine | Sub-millisecond operations |
| Database | PostgreSQL | 16-alpine | Durable persistence and audit |
| Migrations | Flyway | 10.x | Versioned schema evolution |
| Auth Tokens | JJWT | 0.12.5 | HS384 JWT signing |
| Logging | Logback + Logstash | 7.4 | Structured JSON logs |
| Infrastructure | Docker Compose | 3.8 | One-command deployment |

---

# Subscription Tiers

| Plan | Requests/sec | API Keys | Price |
|---|---|---|---|
| Free | 10 | 1 | $0 |
| Pro | 100 | 10 | $49/mo |
| Enterprise | 1,000 | Unlimited | Custom |
| Pay-as-you-go | Unlimited | 1 | $0.01/request |

All limits enforced dynamically through the Drools rules engine.

---

# Quick Start

## Prerequisites

- Docker
- Docker Compose
- Java 17+
- Maven 3.9+

---

## One-Command Deploy

```bash
git clone https://github.com/SibandaPanashe/janus-api-gateway.git

cd janus-api-gateway

# Generate SSL certificates
mkdir -p nginx/ssl

cd nginx/ssl

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout janus.key \
  -out janus.crt \
  -subj "/CN=api.janus.local"

cd ../..

# Add local DNS mapping
echo "127.0.0.1 api.janus.local" | sudo tee -a /etc/hosts

# Configure environment
cp .env.example .env

# Start full platform
docker-compose up -d --build

# Verify services
curl http://localhost:8080/api/health

curl -k https://api.janus.local/api/health
```

---

## Create an API Key

```bash
curl -X POST http://localhost:8080/admin/keys \
  -H "Content-Type: application/json" \
  -d '{
    "plan": "pro",
    "clientId": "my-app"
  }'
```

---

## Exchange API Key for JWT

```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "sk-YOUR_KEY"
  }'
```

---

## Make Authenticated Requests

### API Key Authentication

```bash
curl -H "X-API-Key: sk-YOUR_KEY" \
  http://localhost:8080/api/v1/proxy
```

### JWT Authentication

```bash
curl -H "Authorization: Bearer YOUR_JWT" \
  http://localhost:8080/api/v1/proxy/jwt
```

---

## Test Rate Limiting

```bash
for i in {1..15}; do
  curl -s \
    -H "X-API-Key: sk-FREE_KEY" \
    http://localhost:8080/api/v1/proxy

  echo ""
done
```

Expected behavior:

- First requests return `200 OK`
- Requests beyond plan limit return `429 Too Many Requests`

---

## Hot-Reload a Rule

```bash
curl -X POST http://localhost:8080/admin/rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Custom Rule",
    "description": "Runtime enterprise rule",
    "priority": 50,
    "drlContent": "package com.sibanda.co.zw.janusgateway.rules;
import com.sibanda.co.zw.janusgateway.model.ClientProfile;
dialect \"mvel\"

rule \"Enterprise Rule\"
when
    $client: ClientProfile(plan == \"enterprise\")
then
    System.out.println(\"Enterprise client: \" + $client.getClientId());
end"
  }'
```

---

# API Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/health` | None | Health check with Redis status |
| `POST` | `/admin/keys` | None | Create API key |
| `POST` | `/auth/token` | None | Exchange API key for JWT |
| `POST` | `/auth/refresh` | None | Refresh JWT token |
| `GET` | `/api/v1/proxy` | `X-API-Key` | API key authenticated request |
| `GET` | `/api/v1/proxy/jwt` | `Bearer Token` | JWT authenticated request |
| `GET` | `/admin/rules` | None | List all rules |
| `GET` | `/admin/rules/active` | None | List active rules |
| `POST` | `/admin/rules` | None | Create runtime rule |
| `PATCH` | `/admin/rules/{id}/toggle` | None | Enable/disable rule |
| `POST` | `/admin/rules/reload` | None | Reload rules from database |
| `GET` | `/actuator/health` | Restricted | Spring Boot health endpoint |
| `GET` | `/actuator/metrics` | Restricted | JVM and HTTP metrics |

---

# Architecture Decision Records

| ADR | Decision |
|---|---|
| ADR-001 | Drools rules engine over hard-coded application logic |
| ADR-002 | Redis sliding windows for rate limit counters |
| ADR-003 | Nginx handles first-line rate limiting |
| ADR-004 | PostgreSQL-backed audit event logging |

---

# Build Roadmap

- [x] Project setup and architecture design
- [x] Nginx edge configuration with layered protection
- [x] Spring Boot core engine
- [x] Redis counters and caching
- [x] Drools integration
- [x] JWT authentication
- [x] PostgreSQL persistence
- [x] Hot-reload rule engine
- [x] Structured audit logging
- [x] Dockerized deployment
- [ ] React admin dashboard
- [ ] Stripe billing integration
- [ ] Multi-region Redis clusters
- [ ] Cloud deployment automation

---

# Engineering Context

This project was designed around production-grade financial systems engineering principles:

- Dynamic business rule evaluation
- Auditability
- Operational safety
- Idempotency
- Structured observability
- Load shedding at the edge

The architecture reflects constraints common in regulated environments where correctness and traceability matter more than rapid feature iteration.

---

# Documentation

- 📖 [User Guide — Step-by-step with curl examples](https://fresh-kingfisher-b5c.notion.site/Janus-API-Gateway-Developer-User-Guide-368b5334756b4e9c819876f7d66d0504)

- 📘 [Interactive API Reference (Redocly)](docs/api-reference.html)

- 🔧 [Swagger UI](http://localhost:8080/swagger-ui.html)

- 📋 [OpenAPI Specification](openapi.json)

- 📦 [Postman Collection](postman/)

- 🏗️ [Architecture Decision Records](docs/adr/)

- ⚙️ [System & Operations Documentation](docs/JANUS_DOCUMENTATION.md)

---

# License

MIT License — see [LICENSE](LICENSE) for details.

---

# Author

Built with intent.

Questions, feedback, and contributions are welcome.

GitHub: https://github.com/SibandaPanashe