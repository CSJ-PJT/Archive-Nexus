# Nexus External Fee Contract

Archive-Nexus can receive synthetic fee events from Archive-Logistics and Archive-Ledger. The contract is used by game/economy simulation flows where Nexus pays logistics and settlement-agency fees.

All events are synthetic demo data. Do not send real customer, finance, card, account, delivery, or personal data.

## Endpoints

```http
POST /api/economy/events/external
POST /api/economy/events/external/bulk
```

## Supported Sources

- `Archive-Logistics`
- `Archive-Ledger`

Any other source is rejected with `400 Bad Request`.

## Supported Cost Types

| Source | Cost types |
| --- | --- |
| Archive-Logistics | `LOGISTICS_SERVICE_FEE_PAID`, `LOGISTICS_DAILY_SETTLEMENT_FEE_PAID` |
| Archive-Ledger | `LEDGER_SETTLEMENT_AGENCY_FEE_PAID`, `LEDGER_RECONCILIATION_FEE_PAID` |

## Request Body

```json
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

## Required Fields

| Field | Rule |
| --- | --- |
| `eventId` | Required, unique |
| `idempotencyKey` | Required, unique |
| `sourceService` | Must be `Archive-Logistics` or `Archive-Ledger` |
| `costType` | Must be one of the supported external fee types |
| `costAmount` | Positive synthetic amount |
| `currency` | Defaults to `KRW` when omitted |
| `hopCount` | Defaults to `0` when omitted |
| `maxHop` | Defaults to `8` when omitted |

## Idempotency

Nexus checks both `eventId` and `idempotencyKey`.

- first receipt creates a cost event
- duplicate receipt returns a successful response with `duplicate=true`
- duplicate receipt does not create another cost event

This keeps Logistics/Ledger retry safe.

## Loop Guard

Every external fee event can include:

- `simulationRunId`
- `settlementCycleId`
- `correlationId`
- `causationId`
- `hopCount`
- `maxHop`

If `hopCount > maxHop`, Nexus rejects the event with `400 Bad Request`.

Fee receipt does not trigger production revenue, Outbox publish, or another fee event. This prevents circular fee loops between Nexus, Logistics, Ledger, and ArchiveOS.

## Bulk Request

```json
{
  "events": [
    {
      "eventId": "LOG-FEE-001",
      "idempotencyKey": "Archive-Logistics:LOGISTICS_SERVICE_FEE_PAID:LOG-FEE-001",
      "sourceService": "Archive-Logistics",
      "costType": "LOGISTICS_SERVICE_FEE_PAID",
      "costAmount": 125000,
      "currency": "KRW",
      "hopCount": 1,
      "maxHop": 8,
      "reason": "Synthetic logistics service fee"
    }
  ]
}
```

Bulk response reports accepted, duplicate, and rejected counts. Rejected events do not stop the rest of the batch.

## PowerShell Examples

```powershell
curl.exe -X POST "http://localhost:8080/api/economy/events/external" `
  -H "Content-Type: application/json" `
  -d "{\"eventId\":\"LOG-FEE-001\",\"idempotencyKey\":\"Archive-Logistics:LOGISTICS_SERVICE_FEE_PAID:LOG-FEE-001\",\"sourceService\":\"Archive-Logistics\",\"costType\":\"LOGISTICS_SERVICE_FEE_PAID\",\"costAmount\":125000,\"currency\":\"KRW\",\"hopCount\":1,\"maxHop\":8,\"reason\":\"Synthetic logistics service fee\"}"

curl.exe "http://localhost:8080/api/nexus-economy/summary"
```
