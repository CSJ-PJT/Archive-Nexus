# API Reference

This document lists the operational APIs used to run and inspect Archive-Nexus locally.

## Outbox

### Summary

```http
GET /api/outbox/summary
```

Returns total event counts, status counts, target counts, and integration status.

### List Events

```http
GET /api/outbox/events
GET /api/outbox/events?targetService=LOGITICS
GET /api/outbox/events?targetService=LEDGER
GET /api/outbox/events?status=PENDING_RETRY
GET /api/outbox/events?status=FAILED
```

Supported filters:

- `targetService`: `LOGITICS`, `LEDGER`, `NONE`, `UNKNOWN`
- `status`: `PENDING`, `PUBLISHED`, `PENDING_RETRY`, `FAILED`, `SKIPPED`
- `limit`: maximum result count

### Event Detail

```http
GET /api/outbox/events/{eventId}
```

Returns one outbox event including payload, routing status, retry count, and last error.

### Generate Synthetic Events

```http
POST /api/outbox/events/generate?count=100&type=mixed
POST /api/outbox/events/generate?count=100&type=logistics
POST /api/outbox/events/generate?count=100&type=ledger
POST /api/outbox/events/generate?count=100&type=approval-risk
```

Generation uses synthetic identifiers only. It must not contain real personal, card, account, or financial user data.

### Publish

```http
POST /api/outbox/events/publish?target=auto&dryRun=true
POST /api/outbox/events/publish?target=logitics
POST /api/outbox/events/publish?target=ledger
```

Use `dryRun=true` for manual verification before actual publish.

## Integrations

```http
GET /api/integrations/summary
GET /api/archiveos/status
GET /api/platform/manifest
```

These APIs keep returning a Nexus-level response even if an external service is disabled or unavailable.

## Nexus Economy

```http
GET /api/nexus-economy/summary
GET /api/nexus-economy/revenue-events
GET /api/nexus-economy/cost-events
GET /api/nexus-economy/profit-snapshots
POST /api/nexus-economy/daily-close?date=YYYY-MM-DD
POST /api/economy/events/external
POST /api/economy/events/external/bulk
```

The economy APIs expose synthetic revenue, cost, profit, cash balance, and bankruptcy risk for ArchiveOS Survival Mode.

External fee events are accepted only from:

- `Archive-Logistics`
- `Archive-Ledger`

Supported external fee cost types:

- `LOGISTICS_SERVICE_FEE_PAID`
- `LOGISTICS_DAILY_SETTLEMENT_FEE_PAID`
- `LEDGER_SETTLEMENT_AGENCY_FEE_PAID`
- `LEDGER_RECONCILIATION_FEE_PAID`

Every external fee event should include `eventId`, `idempotencyKey`, `simulationRunId`, `settlementCycleId`, `correlationId`, `causationId`, `hopCount`, and `maxHop`. If `hopCount > maxHop`, Nexus rejects the event to prevent circular fee loops.

Example:

```http
POST /api/economy/events/external
Content-Type: application/json

{
  "eventId": "LOG-FEE-20260709-000001",
  "idempotencyKey": "Archive-Logistics:LOGISTICS_SERVICE_FEE_PAID:LOG-FEE-20260709-000001",
  "simulationRunId": "SIM-DEMO-001",
  "settlementCycleId": "CYCLE-20260709",
  "correlationId": "corr-demo-001",
  "causationId": "route-cost-001",
  "hopCount": 1,
  "maxHop": 8,
  "sourceService": "Archive-Logistics",
  "costType": "LOGISTICS_SERVICE_FEE_PAID",
  "costAmount": 125000,
  "currency": "KRW",
  "reason": "Synthetic logistics service fee billed to Nexus"
}
```

## Simulator

```http
GET /api/simulator/status
POST /api/simulator/start
POST /api/simulator/stop
GET /api/simulator/persistence
```

The simulator status should be checked after start/stop operations to verify actual runtime state.

## Dashboard

```http
GET /api/dashboard/summary
GET /api/factories
GET /api/production
GET /api/quality
GET /api/maintenance
```

These endpoints are used by the frontend dashboard and should remain available even when external integrations are down.

