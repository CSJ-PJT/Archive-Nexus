# Archive-Logistics Daily Settlement Inbox

Archive-Nexus receives synthetic daily manufacturing settlement callbacks from Archive-Logistics.

This is an inbox contract. Nexus stores the callback for operations review, audit, ArchiveOS visibility,
and future dashboard integration. The callback does not directly mutate production, quality, maintenance,
inventory, or simulator source records.

All data must be Synthetic Data / Demo Data. Do not send real customer, card, account, shipment, or map data.

## API

```http
POST /api/logistics/settlements/daily
POST /api/logistics/settlements/daily/bulk
GET /api/logistics/settlements/daily
GET /api/logistics/settlements/daily?factoryId=FAC-A
GET /api/logistics/settlements/daily/{settlementId}
GET /api/logistics/settlements/summary
```

## Request

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

## Idempotency

Nexus treats either repeated `settlementId` or repeated `idempotencyKey` as duplicate-safe.

- Existing row is returned with `duplicate=true`.
- No second settlement row is created.
- `duplicate_count` is incremented.
- Manufacturing source data is not changed.

## Database

Flyway migration:

```text
backend/src/main/resources/db/migration/V9__add_logistics_daily_settlement_inbox.sql
```

Table:

```text
nexus_logistics_daily_settlement
```

Key columns:

- `settlement_id`
- `idempotency_key`
- `settlement_date`
- `factory_id`
- `total_logistics_cost`
- `manufacturing_impact_cost`
- `evidence_json`
- `payload_json`
- `duplicate_count`

## ArchiveOS visibility

ArchiveOS can later read Nexus settlement status through:

```http
GET /api/logistics/settlements/summary
GET /api/logistics/settlements/daily
```
