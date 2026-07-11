# Archive Runtime Mesh V1 · Archive-Nexus

Archive-Nexus는 제조, 품질, 정비, 출하와 운영 인력 처리 결과를 **Synthetic Runtime Data**로 투영한다. 실제 고객, 직원, 주소, 결제·금융 정보는 저장하거나 전송하지 않는다.

## Read API

| API | 용도 | 쓰기 여부 |
| --- | --- | --- |
| `GET /api/runtime/status` | autorun 스케줄러와 파이프라인 상태 | 읽기 전용 |
| `GET /api/runtime-events/recent?limit=100` | 최신 Runtime Event | 읽기 전용 |
| `GET /api/runtime-events/recent?after={cursor}&limit=100` | cursor 이후에 발생한 Event pull | 읽기 전용 |
| `GET /api/runtime-events/correlation/{correlationId}` | 상관관계 추적 | 읽기 전용 |
| `GET /api/runtime-events/entity/{entityId}` | 주문/출하/근무일 등 엔터티 추적 | 읽기 전용 |
| `GET /api/operations/summary` | ArchiveOS 관제용 운영 요약 | 읽기 전용 |
| `GET /api/workforce/summary` | 합성 workforce 요약 | 읽기 전용 |
| `GET /api/productivity/summary` | 합성 생산성 요약 | 읽기 전용 |
| `GET /api/capacity/summary` | 처리 가능 용량과 병목 | 읽기 전용 |

Summary 및 Runtime Event GET 요청은 seed, simulation, outbox publish, settlement 또는 DB insert를 수행하지 않는다.

## 공통 Runtime Event 필드

모든 projection은 `eventId`, `idempotencyKey`, `sourceService`, `targetService`, `domain`, `eventType`, `entityType`, `entityId`, `correlationId`, `causationId`, `simulationRunId`, `settlementCycleId`, `workdayId`, `status`, `severity`, `occurredAt`, `hopCount`, `maxHop`, `metadata`를 제공한다.

`status`는 `CREATED`, `PROCESSING`, `WAITING`, `COMPLETED`, `DELAYED`, `FAILED`, `SETTLED` 계열의 대문자 표시값으로 투영한다. `metadata`에는 synthetic 식별자와 운영 지표만 포함하며 secret, password, webhook, private key, 주소, 전화번호, 카드·계좌번호는 제외한다.

## Cursor pull

`after`는 `occurredAtEpochMillis|eventId` 형식의 cursor다. `GET /api/runtime/status`의 `latestCursor`를 저장한 뒤 다음 폴링에 전달할 수 있다. cursor가 없거나 형식이 맞지 않으면 기존 최신 이벤트 조회와 동일하게 동작한다. 이벤트는 삭제하지 않으며, 동일 eventId는 idempotencyKey와 도메인 저장소의 중복 방지 규칙을 따른다.

## ArchiveOS 전송

현재 Nexus의 Runtime Mesh 기본 방식은 **pull**이다. ArchiveOS가 `/api/live-flow/events/ingest`의 확정된 payload/인증 계약을 제공한 환경에서만 별도 enabled integration을 통해 push를 추가할 수 있다. 그 전에는 외부 write를 시도하지 않고, ArchiveOS가 중단되어도 Nexus 제조·워크포스 처리와 로컬 Runtime Event 조회는 계속 동작한다.
