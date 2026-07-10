# ArchiveOS Live Flow Contract

ArchiveOS Live Flow / Operational Twin은 Archive-Nexus의 실제 runtime 데이터를 read-only로 수집한다.

이 계약은 화면 애니메이션을 위한 가짜 random 데이터를 만들지 않는다. 응답은 Nexus가 이미 저장한 synthetic runtime data에서 생성된다.

## Read-only API

| API | 목적 |
| --- | --- |
| `GET /api/runtime-events/recent?limit=100` | 최신 Market inbound + Nexus Outbox runtime event |
| `GET /api/runtime-events/correlation/{correlationId}` | 동일 correlation 흐름 추적 |
| `GET /api/runtime-events/entity/{entityId}` | 주문, 출하, 클레임, factory 등 단일 entity 흐름 추적 |
| `GET /api/operations/summary` | ArchiveOS Operational Twin용 운영 요약 |
| `GET /api/outbox/summary` | Outbox 상태 집계 |
| `GET /api/integrations/summary` | 외부 연동/라우팅/운영 인력 요약 |
| `GET /api/workforce/summary` | synthetic workforce 요약 |
| `GET /api/productivity/summary` | 생산성 요약 |
| `GET /api/capacity/summary` | capacity 요약 |

## 장애 격리

- ArchiveOS가 꺼져 있어도 Nexus는 정상 동작한다.
- 이 API들은 외부 write를 수행하지 않는다.
- Archive-Logistics 또는 Archive-Ledger가 unavailable이어도 runtime event 조회는 Nexus 내부 DB 기준으로 응답한다.

## 데이터 안전 기준

- 모든 데이터는 Synthetic Runtime Data다.
- metadata에는 synthetic ID, 상태, 운영 지표만 포함한다.
- 실제 이름, 전화번호, 주소, 카드번호, 계좌번호, 결제 토큰, secret, webhook, private key는 포함하지 않는다.

## ArchiveOS 사용 방식

ArchiveOS는 `/api/operations/summary`로 서비스 카드 상태를 구성하고, `/api/runtime-events/recent`로 Live Flow 노드를 구성한다.

상세 추적이 필요한 경우:

1. event의 `correlationId`를 사용해 `/api/runtime-events/correlation/{correlationId}` 호출
2. event의 `entityId`를 사용해 `/api/runtime-events/entity/{entityId}` 호출
3. 필요 시 `/api/outbox/events?status=FAILED` 또는 `/api/outbox/events?status=PENDING_RETRY`로 운영 증거 확인
