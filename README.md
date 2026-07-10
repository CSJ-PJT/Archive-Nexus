<p align="center">
  <img src="docs/brand/archive-nexus-lockup.svg" width="900" alt="Archive Nexus" />
</p>

# Archive-Nexus

Archive-Nexus는 제조·출하 이벤트를 생성하고 물류·정산 흐름으로 연결하는 Manufacturing AX 백엔드입니다.

## Service Role

Archive-Nexus는 Factory A/B/C의 생산, 품질, 재고, 물류, 정비 데이터를 생성하고 운영 API와 대시보드로 노출합니다. 제조 도메인에서 발생한 synthetic event는 Outbox에 저장한 뒤 `eventType` 기준으로 외부 서비스에 라우팅합니다.

- Factory A/B/C 제조 이벤트 생성
- Outbox 기반 이벤트 저장과 재처리 상태 관리
- `eventType` 기반 Archive-Logistics / Archive-Ledger 라우팅
- 외부 서비스 장애 시 제조 API 격리
- ArchiveOS 관제를 위한 status, summary, interaction API 제공

## Core Flow

```text
Factory Runtime
  -> Nexus Outbox
  -> Routing Policy
     -> Logistics events -> Archive-Logistics
     -> Cost/settlement events -> Archive-Ledger
     -> Pre-cost events -> NONE/SKIPPED
```

| Target | Event types | Role |
| --- | --- | --- |
| `LOGITICS` | `LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`, `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT` | Route, ETA, delay, logistics cost calculation by Archive-Logistics |
| `LEDGER` | `PRODUCTION_COMPLETED`, `MATERIAL_CONSUMED`, `MAINTENANCE_COMPLETED`, `QUALITY_DEFECT_DETECTED`, `EMERGENCY_PURCHASE_REQUESTED`, `QUALITY_CLAIM_CHARGED`, `CORPORATE_CARD_USED`, `VENDOR_PAYMENT_REQUESTED` | Cost, approval, ledger, settlement input for Archive-Ledger |
| `NONE` | `SHIPMENT_HOLD_CREATED` | Internal pre-cost state, no external publish |
| `UNKNOWN` | Unsupported event type | Not published, reported as skipped/failed routing |

`Archive-Logistics` is the external service name. Internal compatibility values such as `LOGITICS`, `logitics`, and `ARCHIVE_INTEGRATIONS_LOGITICS_*` remain unchanged for existing API, database, and environment compatibility.

## Main APIs

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/outbox/summary` | Outbox counts by status and target service |
| `GET` | `/api/integrations/summary` | Archive-Logistics / Archive-Ledger configuration and health summary |
| `GET` | `/api/events/market` | List inbound Archive-Market events (`limit`, `status` filter) |
| `GET` | `/api/outbox/events` | Outbox event list with `status` and `targetService` filters |
| `GET` | `/api/outbox/events/{eventId}` | Single outbox event lookup |
| `POST` | `/api/outbox/events/generate?count=100&type=logistics` | Generate synthetic events |
| `POST` | `/api/outbox/events/publish?target=auto&dryRun=true` | Route or publish outbox candidates |
| `POST` | `/api/events/market` | Receive Archive-Market synthetic order/production/shipment/claim events |
| `POST` | `/api/events/market/bulk` | Receive Archive-Market event batch |
| `GET` | `/api/workforce/summary` | Synthetic workforce capacity, backlog, and cost summary |
| `GET` | `/api/productivity/summary` | Latest workday productivity result |
| `GET` | `/api/capacity/summary` | Baseline or assigned workforce capacity summary |
| `POST` | `/api/workforce/allocations` | Receive synthetic workforce allocation from ArchiveOS or Archive-Market |
| `POST` | `/api/workforce/workday/run?date=YYYY-MM-DD` | Record a synthetic workday productivity snapshot |
| `POST` | `/api/logistics/settlements/daily` | Receive synthetic daily manufacturing settlement from Archive-Logistics |
| `GET` | `/api/logistics/settlements/summary` | Inspect received Logistics settlement callback status |
| `GET` | `/api/archiveos/status` | ArchiveOS availability state |
| `GET` | `/api/platform/manifest` | Archive Suite application contract |

## Operational Principles

- Docker/local demo configuration enables Archive-Logistics and Archive-Ledger publishing by default.
- A scheduled publisher runs `target=auto` on the configured interval and keeps pending events moving.
- `dryRun=true` is still available for diagnostics or manual safety checks.
- `retry_count`, `last_error`, `last_publish_target`, and `last_publish_attempt_at` preserve retry evidence.
- `target_service` records whether an event is routed to `LOGITICS`, `LEDGER`, `NONE`, or `UNKNOWN`.
- Set `ARCHIVE_INTEGRATIONS_*_ENABLED=false` to isolate Nexus when a downstream service must be held.
- External service failures must not terminate simulator, dashboard, or manufacturing APIs.

## Language Support

The frontend supports 한국어, English, 日本語, and 简体中文. Operators can switch language from the globe menu in the top-right corner.

- Default locale: `ko`
- Persisted localStorage key: `archive.locale`
- Legacy compatibility key: `archive-nexus-language`
- Unsupported locale values fall back to Korean.
- UI labels are translated, but API paths, event types, enum values, IDs, repository names, and product names remain unchanged.
- Internal compatibility keys such as `logitics` may remain for existing API, DB, and environment contracts while external documentation uses `Archive-Logistics`.

## Local Run

```powershell
docker compose up --build -d
docker compose ps
```

| Service | URL |
| --- | --- |
| Frontend | `http://localhost:15173` |
| Backend health | `http://localhost:8080/actuator/health` |
| Prometheus | `http://localhost:19090` |
| Grafana | `http://localhost:13000` |

Default external integration values:

```env
ARCHIVE_INTEGRATIONS_LOGITICS_ENABLED=true
ARCHIVE_INTEGRATIONS_LOGITICS_BASE_URL=http://host.docker.internal:8092
ARCHIVE_INTEGRATIONS_LEDGER_ENABLED=true
ARCHIVE_INTEGRATIONS_LEDGER_BASE_URL=http://host.docker.internal:18080
ARCHIVE_INTEGRATIONS_LOGITICS_TIMEOUT_MS=30000
ARCHIVE_INTEGRATIONS_LEDGER_TIMEOUT_MS=30000
ARCHIVE_INTEGRATIONS_ROUTING_ALLOW_LEDGER_DIRECT_FALLBACK_FOR_LOGISTICS=false
ARCHIVE_INTEGRATIONS_ROUTING_PUBLISH_INTERVAL_MS=15000
ARCHIVE_WORKFORCE_ENABLED=false
ARCHIVE_WORKFORCE_BASELINE_CAPACITY=120
SPRING_TASK_SCHEDULING_POOL_SIZE=4
```

Do not commit `.env`, tokens, webhooks, private keys, or local data directories.

## Smoke Test

```powershell
curl.exe "http://localhost:8080/api/outbox/summary"
curl.exe "http://localhost:8080/api/integrations/summary"
curl.exe "http://localhost:8080/api/events/market"
curl.exe "http://localhost:8080/api/workforce/summary"
curl.exe "http://localhost:8080/api/capacity/summary"

curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=logistics"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=auto"

curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=ledger"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=ledger"

curl.exe "http://localhost:8080/api/logistics/settlements/summary"
```

Expected behavior:

- Logistics generation creates events routed to `LOGITICS`.
- Ledger generation creates events routed to `LEDGER`.
- Archive-Logistics daily settlement callbacks are stored idempotently and do not mutate manufacturing source data.
- Workforce summaries use synthetic capacity only and do not include real employee or payroll data.
- Enabled downstream services receive published events automatically or through the manual publish commands above.
- Disabled external services are reported as `DISABLED`; Nexus remains `HEALTHY`.

Market inbound smoke (archive-mock traffic):

```powershell
curl.exe -X POST "http://localhost:8080/api/events/market" -H "Content-Type: application/json" -d "{\"eventId\":\"MK-ORDER-001\",\"idempotencyKey\":\"K-001\",\"source\":\"Archive-Market\",\"eventType\":\"MARKET_ORDER_PLACED\",\"schemaVersion\":1,\"occurredAt\":\"2026-07-10T00:00:00Z\",\"simulationRunId\":\"SIM-001\",\"settlementCycleId\":\"CYCLE-001\",\"correlationId\":\"CORR-001\",\"causationId\":\"CAUSE-001\",\"hopCount\":0,\"maxHop\":8,\"payload\":{\"orderId\":\"ORD-001\",\"customerId\":\"CUST-001\",\"customerType\":\"CONSUMER\",\"productType\":\"BATTERY_PACK\",\"quantity\":10,\"orderAmount\":1200000,\"priority\":\"NORMAL\",\"requiresShipment\":true}}"
curl.exe -X POST "http://localhost:8080/api/events/market" -H "Content-Type: application/json" -d "{\"eventId\":\"MK-PROD-001\",\"idempotencyKey\":\"K-002\",\"source\":\"Archive-Market\",\"eventType\":\"PRODUCTION_REQUESTED\",\"schemaVersion\":1,\"occurredAt\":\"2026-07-10T00:00:00Z\",\"simulationRunId\":\"SIM-001\",\"settlementCycleId\":\"CYCLE-001\",\"correlationId\":\"CORR-001\",\"causationId\":\"CAUSE-002\",\"hopCount\":0,\"maxHop\":8,\"payload\":{\"orderId\":\"ORD-001\",\"customerId\":\"CUST-001\",\"customerType\":\"CONSUMER\",\"riskLevel\":\"LOW\",\"productType\":\"BATTERY_PACK\",\"quantity\":10,\"orderAmount\":1200000,\"priority\":\"NORMAL\",\"requiresShipment\":true}}"
curl.exe -X POST "http://localhost:8080/api/events/market" -H "Content-Type: application/json" -d "{\"eventId\":\"MK-SHIP-001\",\"idempotencyKey\":\"K-003\",\"source\":\"Archive-Market\",\"eventType\":\"SHIPMENT_REQUESTED\",\"schemaVersion\":1,\"occurredAt\":\"2026-07-10T00:00:00Z\",\"simulationRunId\":\"SIM-001\",\"settlementCycleId\":\"CYCLE-001\",\"correlationId\":\"CORR-001\",\"causationId\":\"CAUSE-003\",\"hopCount\":0,\"maxHop\":8,\"payload\":{\"shipmentId\":\"SHIP-001\",\"orderId\":\"ORD-001\",\"originCode\":\"FAC-A\",\"destinationCode\":\"DC-SEOUL-01\",\"requiresShipment\":true,\"priority\":\"HIGH\",\"itemType\":\"battery-module\",\"quantity\":5}}"
```

## Verification

```powershell
cd backend
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat bootJar --no-daemon --console=plain

cd ..
docker compose config --quiet
```

## Incident Runbook

1. Check `/api/integrations/summary`.
2. Check `/api/outbox/summary`.
3. Inspect failed or retrying events:

```powershell
curl.exe "http://localhost:8080/api/outbox/events?status=PENDING_RETRY"
curl.exe "http://localhost:8080/api/outbox/events?status=FAILED"
```

4. If an external service is down, keep integration disabled or use `dryRun=true`.
5. After recovery, publish only the target that recovered:

```powershell
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=logitics"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=ledger"
```

## Operations Docs

- [Architecture](docs/architecture.md)
- [Outbox routing](docs/outbox-routing.md)
- [API reference](docs/api-reference.md)
- [Operational workforce](docs/operational-workforce.md)
- [Smoke test](docs/smoke-test.md)
- [Operations runbook](docs/operations-runbook.md)

## Tech Stack

- Java 21, Spring Boot 3, Spring Data JPA
- PostgreSQL, Flyway
- React, Vite, TypeScript, Nginx
- Docker Compose
- Actuator, Prometheus, Grafana
