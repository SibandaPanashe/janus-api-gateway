# ADR-001: Drools Rules Engine Over Hard-Coded Application Logic

## Status
Proposed

## Context
Rate limiting and billing rules change frequently and are owned by
business stakeholders, not engineers. Hard-coding these rules in Java
means every rule change requires a developer, a code review, a build,
and a deployment — which in a regulated environment can take days.

## Decision
Use Drools as the rules engine. Rules are stored as `.drl` files in
the database and hot-reloaded at runtime. Business stakeholders can
modify rules through the admin interface without touching application code.

## Consequences
- Rule changes do not require redeployment ✅
- Rules are testable in isolation ✅
- Adds Drools as a dependency with its own learning curve ⚠️
- Rule debugging requires familiarity with Drools tooling ⚠️
