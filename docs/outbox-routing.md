# Archive-Nexus Outbox Routing

Archive-Nexus no longer treats every synthetic manufacturing event as a direct financial event.
The outbox routes each event by `eventType` so that logistics events first go to Archive-Logistics,
and cost/settlement events go directly to Archive-Ledger.

Archive-Nexus는 제조·출하 이벤트를 생성하고 Outbox 라우팅 정책에 따라 물류 이벤트는 Archive-Logistics로, 정비·구매·품질·카드성 비용 이벤트는 Archive-Ledger로 전달하는 Manufacturing AX 백엔드입니다. 외부 서비스 장애가 제조 API로 전파되지 않도록 target별 retry, dry-run, routing summary, last_error를 제공합니다.

## Why not send everything to Ledger?

`LOGISTICS_DISPATCHED` is not a finalized cost. Route selection, ETA, delay risk, reroute cost,
cold-chain handling, and emergency delivery surcharges belong to Archive-Logistics. Ledger should
receive financial events after those operational costs are confirmed.

## Target policy

| Target | Event types | Meaning |
| --- | --- | --- |
| `LOGITICS` | `LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`, `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT` | Logistics route/cost calculation |
| `LEDGER` | `PRODUCTION_COMPLETED`, `MATERIAL_CONSUMED`, `MAINTENANCE_COMPLETED`, `QUALITY_DEFECT_DETECTED`, `EMERGENCY_PURCHASE_REQUESTED`, `QUALITY_CLAIM_CHARGED`, `CORPORATE_CARD_USED`, `VENDOR_PAYMENT_REQUESTED` | Synthetic financial normalization, ledger, approval, settlement |
| `NONE` | `SHIPMENT_HOLD_CREATED` | Internal operational state; no confirmed external cost yet |
| `UNKNOWN` | Unknown or unsupported event type | Not published; reported as skipped/failed routing |

`Archive-Logistics` is the external service name. The internal target value `LOGITICS`, the publish
query value `logitics`, and `ARCHIVE_INTEGRATIONS_LOGITICS_*` configuration keys remain unchanged
to preserve compatibility with existing APIs, database rows, and environment variables.

## Publish modes

```http
POST /api/outbox/events/publish?target=auto
POST /api/outbox/events/publish?target=logitics
POST /api/outbox/events/publish?target=ledger
POST /api/outbox/events/publish?target=auto&dryRun=true
```

- `auto`: uses `OutboxRoutingPolicy`.
- `logitics`: publishes only events routed to Archive-Logistics.
- `ledger`: publishes only direct Ledger events. Logistics events are excluded unless fallback is explicitly enabled.
- `dryRun=true`: does not call external services. It returns the routing decision and candidate counts.

## Disabled integrations

Default configuration keeps both integrations disabled:

```env
ARCHIVE_INTEGRATIONS_LOGITICS_ENABLED=false
ARCHIVE_INTEGRATIONS_LEDGER_ENABLED=false
```

When disabled, publish calls do not perform HTTP requests and do not increase `retry_count`.
The result reports skipped candidates and the events remain available for later publishing.

## Failure isolation

If Archive-Logistics or Archive-Ledger is unavailable:

- Nexus manufacturing APIs stay available.
- Outbox publish returns HTTP 200 with target-level failure counts.
- Failed events record `retry_count`, `last_error`, `last_publish_target`, and `last_publish_attempt_at`.
- Events move to `PENDING_RETRY`, then `FAILED` after `archive.integrations.routing.max-retry-count`.

## Routing summary for ArchiveOS

ArchiveOS can poll:

```http
GET /api/outbox/summary
GET /api/integrations/summary
GET /api/outbox/events?targetService=LOGITICS
GET /api/outbox/events?targetService=LEDGER
GET /api/outbox/events?status=FAILED
GET /api/outbox/events?status=PENDING_RETRY
```

These endpoints are read-only except for explicit generate/publish calls.
