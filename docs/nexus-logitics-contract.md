# Nexus to Archive-Logistics Contract

Archive-Nexus sends logistics events to Archive-Logistics for synthetic route, ETA, delay, reroute,
and logistics cost calculation.

The public service name is `Archive-Logistics`. The file name, environment keys, and target values
that contain `logitics` or `LOGITICS` are kept for compatibility with the existing Nexus API and
database routing values.

## Endpoint

```http
POST {ARCHIVE_INTEGRATIONS_LOGITICS_BASE_URL}/api/events/nexus/bulk
```

Default local URL:

```text
http://localhost:8092/api/events/nexus/bulk
```

Docker Desktop host URL:

```env
ARCHIVE_INTEGRATIONS_LOGITICS_BASE_URL=http://host.docker.internal:8092
```

## Bulk request

```json
{
  "events": [
    {
      "eventId": "NX-EVT-ABC123",
      "idempotencyKey": "synthetic:LOGISTICS:LOGISTICS_DISPATCHED:SHIP-20260709-000001",
      "source": "Archive-Nexus",
      "eventType": "LOGISTICS_DISPATCHED",
      "schemaVersion": 1,
      "occurredAt": "2026-07-09T10:32:15Z",
      "payload": {
        "synthetic": true,
        "factoryId": "FAC-A",
        "shipmentId": "SHIP-20260709-000001",
        "originCode": "FAC-A",
        "destinationCode": "DC-SEOUL-01",
        "priority": "HIGH",
        "itemType": "battery-module",
        "quantity": 120,
        "requiresColdChain": false
      }
    }
  ]
}
```

## Logistics event types

- `LOGISTICS_DISPATCHED`
- `URGENT_DELIVERY_REQUESTED`
- `SHIPMENT_HOLD_RELEASED`
- `MATERIAL_TRANSFER_REQUESTED`
- `QUALITY_REPLACEMENT_SHIPMENT`

## Idempotency key

The key is synthetic and contains no real customer, card, or account data.
Archive-Logistics should treat repeated `eventId` or `idempotencyKey` as duplicate-safe.

## Retry and timeout

- Timeout: `ARCHIVE_INTEGRATIONS_LOGITICS_TIMEOUT_MS`, default `3000`.
- Disabled: no HTTP call; result is skipped/dry-run.
- Connection refused, timeout, or 5xx: Nexus records failure on the outbox event and keeps manufacturing APIs alive.

## Expected downstream behavior

Archive-Logistics calculates logistics cost and emits a finalized logistics cost event to Archive-Ledger.
Nexus does not directly send `LOGISTICS_DISPATCHED` to Ledger by default.
