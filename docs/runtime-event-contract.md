# Runtime Event Contract

Archive-Nexus는 ArchiveOS Live Flow가 사용할 수 있는 공통 runtime event 응답을 제공한다.

## API

```http
GET /api/runtime-events/recent?limit=100
GET /api/runtime-events/correlation/{correlationId}
GET /api/runtime-events/entity/{entityId}
```

## 응답 필드

```json
{
  "eventId": "runtime or outbox event id",
  "sourceService": "Archive-Nexus",
  "domain": "manufacturing",
  "eventType": "PRODUCTION_COMPLETED",
  "entityType": "production-order",
  "entityId": "ORD-001",
  "correlationId": "CORR-001",
  "causationId": "CAUSE-001",
  "status": "completed",
  "severity": "info",
  "displayLabel": "PRODUCTION_COMPLETED routed to LEDGER",
  "occurredAt": "2026-07-10T00:00:00Z",
  "metadata": {}
}
```

## status 값

| Runtime status | 의미 |
| --- | --- |
| `created` | 수신/생성됨 |
| `moving` | 이동 중인 흐름에 사용 가능 |
| `waiting` | Outbox 대기 |
| `approval_required` | 승인 필요 상태에 사용 가능 |
| `approved` | 승인 완료 상태에 사용 가능 |
| `rejected` | 반려 상태에 사용 가능 |
| `delayed` | 재시도 또는 지연 |
| `failed` | 처리 실패 |
| `completed` | 처리 완료 또는 외부 발행 완료 |
| `settled` | 정산 완료 상태에 사용 가능 |
| `unavailable` | 외부 서비스 unavailable 상태에 사용 가능 |

Archive-Nexus MVP 매핑:

- Market `RECEIVED` → `created`
- Market `PROCESSED` / `DUPLICATE` → `completed`
- Market `REJECTED` / `FAILED` → `failed`
- Outbox `PENDING` → `waiting`
- Outbox `PENDING_RETRY` → `delayed`
- Outbox `FAILED` → `failed`
- Outbox `PUBLISHED` / `SKIPPED` → `completed`

## metadata 정책

허용:

- synthetic `orderId`, `shipmentId`, `returnId`, `claimId`
- `factoryId`, `workdayId`, `workforceAllocationId`
- `targetService`, `retryCount`, `lastError`
- `simulationRunId`, `settlementCycleId`
- `priority`, `riskLevel`, `quantity`, `productType`

금지:

- 실제 이름
- 전화번호
- 주소
- 카드번호
- 계좌번호
- 실제 결제 token
- secret, password, webhook, private key

## 데이터 원천

- `nexus_market_event`
- `nexus_outbox_event`
- workforce summary API

별도 random/fake animation 데이터를 생성하지 않는다.
