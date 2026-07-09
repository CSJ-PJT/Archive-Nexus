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

## Archive-Logistics Daily Settlement Callback

Archive-Nexus receives synthetic daily manufacturing settlement callbacks from Archive-Logistics.
The callback stores evidence and calculated cost impact for operations review; it does not mutate
factory production, quality, maintenance, or inventory source data.

```http
POST /api/logistics/settlements/daily
POST /api/logistics/settlements/daily/bulk
GET /api/logistics/settlements/daily
GET /api/logistics/settlements/daily?factoryId=FAC-A
GET /api/logistics/settlements/daily/{settlementId}
GET /api/logistics/settlements/summary
```

Minimal request:

```json
{
  "settlementId": "LGS-SETTLE-20260709-FAC-A",
  "idempotencyKey": "LOGISTICS:DAILY:2026-07-09:FAC-A",
  "source": "Archive-Logistics",
  "schemaVersion": 1,
  "settlementDate": "2026-07-09",
  "factoryId": "FAC-A",
  "currency": "KRW",
  "totalShipments": 12,
  "delayedShipments": 2,
  "heldShipments": 1,
  "totalQuantity": 1440,
  "totalLogisticsCost": 3800000,
  "manufacturingImpactCost": 720000,
  "onTimeRate": 0.8333,
  "evidence": {
    "basis": "synthetic daily route cost summary"
  },
  "payload": {
    "demoData": true
  },
  "occurredAt": "2026-07-09T10:00:00Z"
}
```

Repeated `settlementId` or `idempotencyKey` is treated as duplicate-safe and returns `duplicate=true`
without creating another settlement row.

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
