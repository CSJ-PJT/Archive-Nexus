# Nexus Runtime Event Contract

Archive-Nexus는 ArchiveOS Live Flow와 Operational Twin이 읽을 수 있도록 Market inbound, 제조 Outbox, 운영 인력, workday 결과를 공통 runtime event 형식으로 노출한다.

## API

```http
GET /api/runtime-events/recent?limit=100
GET /api/runtime-events/correlation/{correlationId}
GET /api/runtime-events/entity/{entityId}
```

## Runtime event 공통 필드

| 필드 | 설명 |
| --- | --- |
| `eventId` | runtime projection event id |
| `sourceService` | 이벤트를 노출하는 서비스. Nexus Outbox projection은 `Archive-Nexus`로 통일 |
| `domain` | `market`, `production`, `manufacturing`, `quality`, `maintenance`, `logistics`, `workforce` |
| `eventType` | Live Flow에서 표시할 이벤트 타입 |
| `entityType` | `order`, `shipment`, `production-order`, `workforce-allocation`, `workday` 등 |
| `entityId` | synthetic entity id |
| `correlationId` | cross-service trace id |
| `causationId` | 원인 event id |
| `status` | `created`, `moving`, `waiting`, `delayed`, `failed`, `completed`, `rejected` 등 |
| `severity` | `info`, `warning`, `critical` |
| `displayLabel` | UI 표시용 요약 |
| `occurredAt` | 발생 시각 |
| `metadata` | synthetic ID와 운영 지표 |

## Nexus runtime event projection

| 원천 | Runtime eventType |
| --- | --- |
| Market `MARKET_ORDER_PLACED` | `MARKET_ORDER_RECEIVED` |
| Market `PRODUCTION_REQUESTED` | `PRODUCTION_REQUESTED` |
| Market `PRODUCTION_REQUESTED` processed | `PRODUCTION_STARTED` |
| Outbox `PRODUCTION_COMPLETED` | `PRODUCTION_COMPLETED` |
| Outbox `PRODUCTION_DELAYED` | `PRODUCTION_DELAYED` |
| Outbox `BACKLOG_INCREASED` | `BACKLOG_INCREASED` |
| Outbox `QUALITY_DEFECT_DETECTED` | `QUALITY_DEFECT_DETECTED` |
| Outbox `MAINTENANCE_COMPLETED` | `MAINTENANCE_COMPLETED` |
| Outbox `MATERIAL_CONSUMED` | `MATERIAL_CONSUMED` |
| Outbox `LOGISTICS_DISPATCHED` | `LOGISTICS_DISPATCHED` |
| Workforce allocation | `WORKFORCE_ALLOCATION_ASSIGNED` |
| Workday result | `WORKDAY_COMPLETED` |
| Workday result with backlog | `CAPACITY_SHORTAGE_DETECTED` |

## sourceService 정책

- Market inbound projection: `Archive-Market`
- Nexus Outbox projection: `Archive-Nexus`
- Workforce allocation projection: 요청 원천이 명확하면 해당 `sourceService`, 아니면 `Archive-Nexus`
- 원천이 Archive-Market인 Outbox 이벤트도 Nexus가 라우팅/발행하는 runtime event이므로 `sourceService=Archive-Nexus`로 노출하고, 원 출처는 `metadata.originSourceService`에 보존한다.

## 데이터 안전 기준

metadata에는 synthetic ID와 운영 지표만 포함한다.

금지:

- 실제 이름
- 전화번호
- 주소
- 카드번호
- 계좌번호
- 실제 결제 token
- secret, password, webhook, private key

## fake data 분리

이 API는 화면 애니메이션용 fake/random 이벤트를 만들지 않는다. 모든 projection은 다음 저장 데이터에서만 생성된다.

- `nexus_market_event`
- `nexus_outbox_event`
- `nexus_workforce_allocation`
- `nexus_workday_result`
