# ADR-001: Drools Rules Engine Over Hard-Coded Application Logic

## Status
**Accepted** (Implemented May 2026)

## Context
Rate limiting and billing rules change frequently and are owned by
business stakeholders, not engineers. Hard-coding these rules in Java
means every rule change requires a developer, a code review, a build,
and a deployment — which in a regulated environment can take days.

## Decision
Use Drools as the rules engine. Rules are stored as `.drl` content in
the PostgreSQL `rules` table and hot-reloaded at runtime via the
`DynamicRuleService`. Business stakeholders can modify rules through
the `/admin/rules` API without touching application code or restarting
the service.

## Implementation Detail

**Rule Storage:** PostgreSQL `rules` table with columns for
`drl_content` (TEXT), `priority` (INT), `is_active` (BOOLEAN),
and `version` (INT).

**Seed Rules (Flyway V002):**
- Free Tier Rate Limit — blocks free clients at ≥10 req/s
- Blocked Client Deny All — rejects all requests from blocked clients
- Pay As You Go Surcharge — adds $0.01 per request for PAYG clients

**Hot-Reload Mechanism:** `DynamicRuleService.reloadRules()` reads all
active rules from PostgreSQL, compiles them into a new `KieContainer`,
and atomically swaps the reference. All subsequent requests use the new
rules immediately.

**Admin API:** `POST /admin/rules` (create), `PATCH /admin/rules/{id}/toggle`
(enable/disable), `POST /admin/rules/reload` (manual reload),
`DELETE /admin/rules/{id}` (remove).

## Consequences

| Outcome | Detail |
|---------|--------|
| ✅ Rule changes require no redeployment | Confirmed. Rules created via API take effect immediately. |
| ✅ Rules are testable in isolation | Each rule is a self-contained DRL snippet; can be tested individually. |
| ⚠️ Drools dependency with learning curve | Drools 10.0.0 with MVEL dialect. Team familiarity required for DRL authoring. |
| ⚠️ Rule debugging requires Drools tooling | Compilation errors surfaced via API; no visual debugger in MVP. |
| ✅ Seed rules provide baseline | Free tier rate limiting works out of the box. |
| ✅ Version tracking | Each rule update increments the version column for audit. |

## References
- Drools 10 documentation: https://drools.org/
- Implementation: `DynamicRuleService.java`, `RuleAdminController.java`
- Migration: `V002__create_rules_table.sql`