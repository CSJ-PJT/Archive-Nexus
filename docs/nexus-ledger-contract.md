# Nexus to Archive-Ledger Contract

Archive-Nexus sends only direct synthetic financial/cost events to Archive-Ledger.
Logistics events are routed to Archive-Logitics first and should reach Ledger only after logistics cost confirmation.

## Endpoint

```http
POST {ARCHIVE_INTEGRATIONS_LEDGER_BASE_URL}/api/events/nexus/bulk
```

Default local URL:

```text
http://localhost:18080/api/events/nexus/bulk
```

Docker Desktop host URL:

```env
ARCHIVE_INTEGRATIONS_LEDGER_BASE_URL=http://host.docker.internal:18080
```

## Bulk request

Archive-Ledger currently accepts an array of events:

```json
[
  {
    "eventId": "NX-EVT-ABC123",
    "idempotencyKey": "synthetic:LEDGER:MAINTENANCE_COMPLETED:MNT-0001",
    "eventType": "MAINTENANCE_COMPLETED",
    "aggregateType": "MaintenanceEvent",
    "aggregateId": "MNT-0001",
    "source": "Archive-Nexus",
    "schemaVersion": 1,
    "occurredAt": "2026-07-09T10:32:15Z",
    "payload": {
      "synthetic": true,
      "factoryId": "FAC-B",
      "equipmentId": "EQ-B-017",
      "vendorId": "VENDOR-MAINT-03",
      "severity": "HIGH",
      "estimatedCost": 4800000,
      "currency": "KRW",
      "requiresApproval": true
    }
  }
]
```

## Direct Ledger event types

- `PRODUCTION_COMPLETED`
- `MATERIAL_CONSUMED`
- `MAINTENANCE_COMPLETED`
- `QUALITY_DEFECT_DETECTED`
- `EMERGENCY_PURCHASE_REQUESTED`
- `QUALITY_CLAIM_CHARGED`
- `CORPORATE_CARD_USED`
- `VENDOR_PAYMENT_REQUESTED`

## Excluded logistics event

`LOGISTICS_DISPATCHED` is not sent to Ledger directly by default.
It must be routed to Archive-Logitics, where route and cost are calculated before Ledger settlement.

Legacy fallback can be enabled only for testing:

```env
ARCHIVE_INTEGRATIONS_ROUTING_ALLOW_LEDGER_DIRECT_FALLBACK_FOR_LOGISTICS=true
```

Default is `false`.

## Failure behavior

Ledger errors are isolated to the outbox event:

- Nexus API remains available.
- `retry_count` increases only for actual HTTP publish attempts.
- Disabled Ledger integration results in skipped/dry-run behavior, not failure.
- `last_error`, `last_publish_target`, `target_url`, and `last_publish_attempt_at` are persisted for operations review.
