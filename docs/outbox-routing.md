# Outbox Routing

Archive-Nexus stores synthetic manufacturing events in `nexus_outbox_event` and routes them by `eventType`. It does not send every event directly to Archive-Ledger. Logistics events first go to Archive-Logistics, where route and cost are calculated.

## Target Policy

| Target | Event types | Publish behavior |
| --- | --- | --- |
| `LOGITICS` | `LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`, `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT` | Send to Archive-Logistics |
| `LEDGER` | `PRODUCTION_COMPLETED`, `MATERIAL_CONSUMED`, `MAINTENANCE_COMPLETED`, `QUALITY_DEFECT_DETECTED`, `EMERGENCY_PURCHASE_REQUESTED`, `QUALITY_CLAIM_CHARGED`, `CORPORATE_CARD_USED`, `VENDOR_PAYMENT_REQUESTED` | Send to Archive-Ledger |
| `NONE` | `SHIPMENT_HOLD_CREATED` | Skip external publish |
| `UNKNOWN` | Unsupported event type | Skip and expose as routing issue |

### Market inbound mapping

Archive-Market inbound events are translated to outbox events and then routed through the same policy.

| Market event | Mapped outbox event | Target outcome |
| --- | --- | --- |
| `MARKET_ORDER_PLACED` | none | Stored for demand visibility only |
| `PRODUCTION_REQUESTED` | `PRODUCTION_COMPLETED` | `LEDGER` |
| `SHIPMENT_REQUESTED` | `LOGISTICS_DISPATCHED` (if `requiresShipment=true`) / `SHIPMENT_HOLD_CREATED` (if `requiresShipment=false`) | `LOGITICS` or `NONE` |
| `ORDER_CANCELLED` | `SHIPMENT_HOLD_CREATED` | `NONE` |
| `RETURN_REQUESTED` | `QUALITY_DEFECT_DETECTED` | `LEDGER` |
| `QUALITY_CLAIM_CREATED` | `QUALITY_CLAIM_CHARGED` | `LEDGER` |

Market payload fields are preserved in mapped payload with metadata namespace `marketPayload`, plus:
`orderId`, `customerId`, `customerType`, `riskLevel`, `productType`, `quantity`, `totalAmount`, `orderAmount`, `priority`, `requiresShipment`, `returnId`, `claimId`, `simulationRunId`, `settlementCycleId`, `correlationId`, `causationId`, `hopCount`, `maxHop`.

`Archive-Logistics` is the external service name. Existing values `LOGITICS`, `logitics`, and `ARCHIVE_INTEGRATIONS_LOGITICS_*` remain in API and configuration for backward compatibility.

## Publish Modes

```http
POST /api/outbox/events/publish?target=auto
POST /api/outbox/events/publish?target=logitics
POST /api/outbox/events/publish?target=ledger
POST /api/outbox/events/publish?target=auto&dryRun=true
```

- `auto`: apply routing policy to the next pending candidates.
- `logitics`: publish only Archive-Logistics target events.
- `ledger`: publish only Archive-Ledger target events.
- `dryRun=true`: calculate route and target counts without external HTTP calls.

## Automatic Publishing

Docker/local demo configuration enables both downstream integrations and runs scheduled `AUTO` routing. Logistics events go to Archive-Logistics first; direct finance events go to Archive-Ledger.

```env
ARCHIVE_INTEGRATIONS_LOGITICS_ENABLED=true
ARCHIVE_INTEGRATIONS_LEDGER_ENABLED=true
ARCHIVE_INTEGRATIONS_ROUTING_PUBLISH_INTERVAL_MS=15000
```

Set either integration flag to `false` when a downstream service must be intentionally isolated. When disabled, publish calls do not call that external service. The response reports skipped candidates, and events remain available for later publish.

## Failure Handling

When a target service is enabled but unavailable:

- publish returns a target-level failure count;
- `retry_count` is increased;
- `last_error` stores the failure reason;
- `last_publish_target` and `last_publish_attempt_at` record the failed attempt;
- status becomes `PENDING_RETRY`, then `FAILED` after max retry count.

## ArchiveOS Polling Surface

ArchiveOS can poll these read APIs:

```http
GET /api/outbox/summary
GET /api/integrations/summary
GET /api/outbox/events?targetService=LOGITICS
GET /api/outbox/events?targetService=LEDGER
GET /api/outbox/events?status=FAILED
GET /api/outbox/events?status=PENDING_RETRY
```
