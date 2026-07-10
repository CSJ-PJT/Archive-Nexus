# Archive-Market Integration Contract (Synthetic Commerce Inbound)

This contract defines how Archive-Nexus receives synthetic commerce events from **Archive-Market** and routes them to existing downstream targets.

## 1) Base URL

All requests from Archive-Market should target:

`POST http://<archive-nexus-host>:8080/api/events/market`

`POST http://<archive-nexus-host>:8080/api/events/market/bulk`

`GET http://<archive-nexus-host>:8080/api/events/market?status=PROCESSED`

## 2) Inbound Event API

### `POST /api/events/market`

Header + payload form:

```json
{
  "eventId": "MK-ORDER-20260101-001",
  "idempotencyKey": "MK-IDEMP-20260101-001",
  "source": "Archive-Market",
  "eventType": "PRODUCTION_REQUESTED",
  "schemaVersion": 1,
  "occurredAt": "2026-01-01T10:00:00Z",
  "simulationRunId": "SIM-001",
  "settlementCycleId": "CYCLE-20260101",
  "correlationId": "CORR-ORDER-001",
  "causationId": "CAUSE-ORDER-001",
  "hopCount": 0,
  "maxHop": 8,
  "payload": {
    "orderId": "ORD-001",
    "customerId": "CUST-ACME",
    "customerType": "ENTERPRISE",
    "riskLevel": "LOW",
    "productType": "BATTERY_PACK",
    "quantity": 10,
    "totalAmount": 1200000,
    "orderAmount": 1200000,
    "priority": "NORMAL",
    "requiresShipment": true,
    "returnId": "RET-001",
    "claimId": "CLM-001"
  }
}
```

- `source` must be `Archive-Market` for explicit external source visibility.
- `eventId` and `idempotencyKey` are used for duplicate rejection.
- `hopCount` and `maxHop` protect cyclic propagation.

Response shape is `MarketEventResponse`.

### `POST /api/events/market/bulk`

```json
{
  "events": [
    { "... single event request ...": "..." },
    { "... single event request ...": "..." }
  ]
}
```

Response is `MarketBulkEventResponse`.

### `GET /api/events/market`

Optional filters:

- `limit` (default 100, max 500)
- `status` (`RECEIVED`, `PROCESSED`, `DUPLICATE`, `REJECTED`, `FAILED`)

## 3) Supported Event Types

`Archive-Nexus` currently accepts:

- `MARKET_ORDER_PLACED`
- `PRODUCTION_REQUESTED`
- `SHIPMENT_REQUESTED`
- `ORDER_CANCELLED`
- `RETURN_REQUESTED`
- `QUALITY_CLAIM_CREATED`

## 4) Routing Rules (to existing Outbox policy)

All incoming Market events are converted to internal outbox events only when synthetic mapping requires downstream processing:

| Market event | Outbox event mapping |
| --- | --- |
| `PRODUCTION_REQUESTED` | `PRODUCTION_COMPLETED` |
| `SHIPMENT_REQUESTED` | `LOGISTICS_DISPATCHED` (if `requiresShipment=true`) |
| `SHIPMENT_REQUESTED` | `SHIPMENT_HOLD_CREATED` (if `requiresShipment=false`) |
| `ORDER_CANCELLED` | `SHIPMENT_HOLD_CREATED` |
| `RETURN_REQUESTED` | `QUALITY_DEFECT_DETECTED` |
| `QUALITY_CLAIM_CREATED` | `QUALITY_CLAIM_CHARGED` |
| `MARKET_ORDER_PLACED` | No immediate outbox emission (demand capture only) |

Routing policy remains unchanged:

- `LOGITICS` target: logistics events only
- `LEDGER` target: financial/cost events only
- `NONE` target: `SHIPMENT_HOLD_CREATED`

## 5) Idempotency and Safe Guard

- Duplicate `idempotencyKey` or duplicate `eventId` returns previous processing result.
- `hopCount > maxHop` is rejected with status `REJECTED`.
- Unknown event types are not rejected via exception, but returned as `FAILED`.
- Payload from Market is preserved into outbox payload under:
  - `marketPayload`
  - `eventId`, `idempotencyKey`, `eventType`, `source`
  - business fields (`orderId`, `customerId`, `customerType`, `riskLevel`, `productType`, `quantity`, `totalAmount`, `orderAmount`, `priority`, `requiresShipment`, `returnId`, `claimId`, metadata IDs)

## 6) Integration Summary & Isolation

`GET /api/integrations/summary` includes:

- `marketInboundEnabled`
- `marketEventsReceived`
- `marketEventsProcessed`
- `marketEventsFailed`
- `marketOriginOutboxEvents`

When `ARCHIVE_INTEGRATIONS_MARKET_ENABLED=false`, API still works and inbound persistence remains isolated.
