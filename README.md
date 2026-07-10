<p align="center">
  <img src="docs/brand/archive-nexus-lockup.svg" width="900" alt="Archive Nexus" />
</p>

# Archive-Nexus

Archive-Nexus는 제조·출하 이벤트를 생성하고, Archive-Market 주문을 생산 흐름으로 받아들이며, 합성 운영 인력의 처리 능력에 따라 생산 처리량·미처리 물량·품질/정비 리스크를 계산하는 Manufacturing AX 백엔드입니다.

Nexus는 생성된 제조 이벤트를 Outbox에 저장한 뒤 `eventType` 기반 라우팅 정책에 따라 Archive-Logistics 또는 Archive-Ledger로 전달합니다. 외부 서비스가 비활성화되거나 중단되어도 제조 API, 시뮬레이터, 대시보드가 중단되지 않도록 장애를 격리합니다.

> 모든 주문, 고객, 금액, 인력, 정산 데이터는 Synthetic Data / Demo Data입니다. 실제 고객 정보, 실제 결제 정보, 실제 배송 주소, 실제 직원/급여/개인정보를 저장하지 않습니다.

## 운영 역할

- Factory A/B/C 제조 런타임 상태 생성 및 조회
- Archive-Market 주문·생산·출하·취소·반품·클레임 이벤트 수신
- 합성 운영 인력 배정 기반 생산 능력, 미처리 물량, 생산성 계산
- 제조/품질/정비/출하 이벤트를 Outbox에 저장
- Logistics 이벤트는 Archive-Logistics로 전달
- 비용·정산성 이벤트는 Archive-Ledger로 직접 전달
- 비용 확정 전 이벤트는 `NONE/SKIPPED` 처리
- ArchiveOS가 읽을 수 있는 연동, Outbox, 운영 인력, 물류 정산 요약 제공

## 전체 흐름

```mermaid
flowchart LR
  Market[Archive-Market<br/>합성 커머스 이벤트]
  Nexus[Archive-Nexus<br/>Manufacturing AX]
  Workforce[운영 인력<br/>처리 능력 / 미처리 물량 / 생산성]
  Outbox[Nexus Outbox<br/>라우팅 정책 / 재시도 / Dry-run]
  Logistics[Archive-Logistics<br/>경로 / ETA / 물류비]
  Ledger[Archive-Ledger<br/>거래 / 원장 / 정산]
  OS[ArchiveOS<br/>관제 타워]

  Market -->|주문 / 생산 / 출하 요청| Nexus
  Nexus --> Workforce
  Workforce -->|제조 이벤트| Outbox
  Outbox -->|물류 대상| Logistics
  Outbox -->|원장 직접 대상| Ledger
  Outbox -->|NONE / SKIPPED| Nexus
  Logistics -->|일일 정산 콜백| Nexus
  Logistics -->|확정 물류비| Ledger
  Nexus -->|요약 API| OS
  Logistics -->|요약 API| OS
  Ledger -->|요약 API| OS
```

## 핵심 기능

### 1. 제조 런타임

- Factory A/B/C 생산, 품질, 정비, 재고, 물류 런타임 상태 유지
- 시뮬레이터 start/stop 및 대시보드 API 제공
- PostgreSQL/JPA 기반 제조 데이터 영속화
- Prometheus/Grafana 관측 구조 유지

### 2. Archive-Market 수신

Nexus는 Archive-Market의 합성 커머스 이벤트를 수신하고, 필요한 경우 제조 Outbox 이벤트로 변환합니다.

| Market 이벤트 | Nexus 처리 |
| --- | --- |
| `MARKET_ORDER_PLACED` | 주문 기반 제조 수요로 저장 |
| `PRODUCTION_REQUESTED` | 운영 인력 처리 능력을 소모해 `PRODUCTION_COMPLETED` 또는 `BACKLOG_INCREASED` 생성 |
| `SHIPMENT_REQUESTED` | `LOGISTICS_DISPATCHED` 또는 `SHIPMENT_HOLD_CREATED` 생성 |
| `ORDER_CANCELLED` | 출하 보류/취소 성격의 내부 이벤트 생성 |
| `RETURN_REQUESTED` | 품질/반품 관련 이벤트로 연결 |
| `QUALITY_CLAIM_CREATED` | `QUALITY_CLAIM_CHARGED` 또는 품질 이벤트로 연결 |

Market-origin payload의 `orderId`, `customerType`, `riskLevel`, `productType`, `quantity`, `orderAmount`, `priority`, `simulationRunId`, `settlementCycleId`, `correlationId`, `causationId`는 하위 서비스 추적을 위해 Outbox payload에 보존됩니다.

### 3. 운영 인력

ArchiveOS 또는 Archive-Market이 합성 운영 인력을 배정하면 Nexus는 해당 처리 능력을 기준으로 생산 가능량과 미처리 물량을 계산합니다.

| 역할 | 기본 의미 |
| --- | --- |
| `PRODUCTION_OPERATOR` | 생산 처리 능력 |
| `QUALITY_INSPECTOR` | 품질 검사 능력 및 품질 리스크 |
| `MAINTENANCE_ENGINEER` | 정비 처리 능력 및 가동 중단 리스크 |
| `MATERIAL_HANDLER` | 자재 처리 능력 |
| `FACTORY_MANAGER` | 운영 관리 능력 |

`archive.workforce.enabled=false`이면 기존 기준 처리 능력으로 동작합니다. 활성 상태에서는 active allocation의 `effectiveCapacity`, `usedCapacity`, `remainingCapacity`를 실제로 소모합니다.

### 4. Outbox 라우팅

| 대상 | 이벤트 타입 | 설명 |
| --- | --- | --- |
| `LOGITICS` | `LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`, `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT` | Archive-Logistics가 route, ETA, 운송비, 지연/우회 비용 계산 |
| `LEDGER` | `PRODUCTION_COMPLETED`, `MATERIAL_CONSUMED`, `MAINTENANCE_COMPLETED`, `QUALITY_DEFECT_DETECTED`, `EMERGENCY_PURCHASE_REQUESTED`, `QUALITY_CLAIM_CHARGED`, `CORPORATE_CARD_USED`, `VENDOR_PAYMENT_REQUESTED` | Archive-Ledger가 거래·원장·정산·대사 처리 |
| `NONE` | `SHIPMENT_HOLD_CREATED`, `PRODUCTION_DELAYED`, `BACKLOG_INCREASED`, `MAINTENANCE_REQUIRED` | 비용 확정 전 또는 내부 운영 상태. 외부 발행 생략 |
| `UNKNOWN` | 지원하지 않는 eventType | 발행하지 않고 라우팅 생략/실패 근거 기록 |

외부 표기는 `Archive-Logistics`로 통일합니다. 내부 호환 값인 `LOGITICS`, `logitics`, `ARCHIVE_INTEGRATIONS_LOGITICS_*`는 기존 DB/API/env 계약 유지를 위해 그대로 둡니다.

## 주요 API

### Outbox / 연동

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/operations/summary` | ArchiveOS Operational Twin용 read-only 운영 요약 |
| `GET` | `/api/runtime-events/recent?limit=100` | 최신 runtime event 조회 |
| `GET` | `/api/runtime-events/correlation/{correlationId}` | correlationId 기준 runtime event 추적 |
| `GET` | `/api/runtime-events/entity/{entityId}` | entityId 기준 runtime event 추적 |
| `GET` | `/api/outbox/summary` | Outbox 상태/대상별 집계 |
| `GET` | `/api/outbox/events` | Outbox 이벤트 목록. `status`, `targetService` 필터 지원 |
| `GET` | `/api/outbox/events/{eventId}` | 단일 Outbox 이벤트 조회 |
| `POST` | `/api/outbox/events/generate?count=100&type=logistics` | 합성 이벤트 생성 |
| `POST` | `/api/outbox/events/publish?target=auto&dryRun=true` | dry-run 라우팅 결과 확인 |
| `POST` | `/api/outbox/events/publish?target=logitics` | Logistics 대상 이벤트 발행 |
| `POST` | `/api/outbox/events/publish?target=ledger` | Ledger 대상 이벤트 발행 |
| `GET` | `/api/integrations/summary` | Archive-Logistics, Archive-Ledger, ArchiveOS, 운영 인력 요약 |

### Archive-Market 수신

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/events/market` | Archive-Market 단건 이벤트 수신 |
| `POST` | `/api/events/market/bulk` | Archive-Market 이벤트 batch 수신 |
| `GET` | `/api/events/market` | Market 수신 이벤트 목록. `status`, `limit` 필터 지원 |

### 운영 인력 / 생산성 / 처리 능력

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/workforce/allocations` | 합성 운영 인력 배정 수신 |
| `GET` | `/api/workforce/summary` | 인원, 처리 능력, 미처리 물량, 인건비 요약 |
| `GET` | `/api/productivity/summary` | 최신 workday 생산성 결과 |
| `GET` | `/api/capacity/summary` | 기준값 또는 배정 기반 처리 능력 요약 |
| `POST` | `/api/workforce/workday/run?date=YYYY-MM-DD` | 합성 workday snapshot 생성 |

### 물류 정산 콜백

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/logistics/settlements/daily` | Archive-Logistics의 합성 일일 제조 정산 수신 |
| `POST` | `/api/logistics/settlements/daily/bulk` | 정산 콜백 batch 수신 |
| `GET` | `/api/logistics/settlements` | 수신 정산 목록 |
| `GET` | `/api/logistics/settlements/summary` | 정산 콜백 요약 |

### Platform / ArchiveOS

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/archiveos/status` | ArchiveOS 가용 상태 |
| `GET` | `/api/platform/manifest` | Archive Suite 앱 계약 |
| `GET` | `/actuator/health` | Spring Boot health |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

## 환경 변수

기본값은 로컬/demo 환경 기준입니다. 실제 secret, token, webhook, private key는 커밋하지 않습니다.

```powershell
# ArchiveOS
ARCHIVEOS_BASE_URL=http://host.docker.internal:4000

# Archive-Logistics 연동
ARCHIVE_INTEGRATIONS_LOGITICS_ENABLED=true
ARCHIVE_INTEGRATIONS_LOGITICS_BASE_URL=http://host.docker.internal:8092
ARCHIVE_INTEGRATIONS_LOGITICS_BULK_ENDPOINT=/api/events/nexus/bulk
ARCHIVE_INTEGRATIONS_LOGITICS_TIMEOUT_MS=30000

# Archive-Ledger 연동
ARCHIVE_INTEGRATIONS_LEDGER_ENABLED=true
ARCHIVE_INTEGRATIONS_LEDGER_BASE_URL=http://host.docker.internal:18080
ARCHIVE_INTEGRATIONS_LEDGER_BULK_ENDPOINT=/api/events/nexus/bulk
ARCHIVE_INTEGRATIONS_LEDGER_TIMEOUT_MS=30000

# 기존 Ledger 호환 키
ARCHIVE_LEDGER_ENABLED=true
ARCHIVE_LEDGER_BASE_URL=http://host.docker.internal:18080
ARCHIVE_LEDGER_TIMEOUT_MS=30000

# 라우팅
ARCHIVE_INTEGRATIONS_ROUTING_MODE=AUTO
ARCHIVE_INTEGRATIONS_ROUTING_CHUNK_SIZE=50
ARCHIVE_INTEGRATIONS_ROUTING_MAX_RETRY_COUNT=5
ARCHIVE_INTEGRATIONS_ROUTING_ALLOW_LEDGER_DIRECT_FALLBACK_FOR_LOGISTICS=false

# Archive-Market / 운영 인력
ARCHIVE_INTEGRATIONS_MARKET_ENABLED=false
ARCHIVE_WORKFORCE_ENABLED=false
ARCHIVE_WORKFORCE_BASELINE_CAPACITY=120
```

## 로컬 실행

```powershell
docker compose up --build -d
```

주요 URL:

| 서비스 | URL |
| --- | --- |
| Nexus 프론트엔드 | `http://localhost:15173` |
| Nexus 백엔드 | `http://localhost:8080` |
| Prometheus | `http://localhost:19090` |
| Grafana | `http://localhost:13000` |

Archive-Logistics, Archive-Ledger, Archive-Market, ArchiveOS는 각 저장소에서 별도로 실행합니다. Nexus docker-compose에는 외부 서비스를 직접 포함하지 않고 URL 기반으로 연동합니다.

## 스모크 테스트

PowerShell 기준 예시입니다.

```powershell
curl.exe "http://localhost:8080/api/integrations/summary"
curl.exe "http://localhost:8080/api/outbox/summary"
curl.exe "http://localhost:8080/api/workforce/summary"

curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=logistics"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=auto&dryRun=true"

curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=ledger"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=auto&dryRun=true"
```

Market 수신 스모크:

```powershell
curl.exe -X POST "http://localhost:8080/api/events/market" `
  -H "Content-Type: application/json" `
  -d "{\"eventId\":\"MK-PROD-001\",\"idempotencyKey\":\"MK-PROD-001\",\"source\":\"Archive-Market\",\"eventType\":\"PRODUCTION_REQUESTED\",\"schemaVersion\":1,\"occurredAt\":\"2026-07-10T00:00:00Z\",\"simulationRunId\":\"SIM-001\",\"settlementCycleId\":\"CYCLE-001\",\"correlationId\":\"CORR-001\",\"causationId\":\"CAUSE-001\",\"hopCount\":0,\"maxHop\":8,\"payload\":{\"orderId\":\"ORD-001\",\"customerId\":\"SYN-CUSTOMER-001\",\"customerType\":\"B2B\",\"riskLevel\":\"LOW\",\"productType\":\"BATTERY_PACK\",\"quantity\":10,\"orderAmount\":1200000,\"priority\":\"NORMAL\",\"requiresShipment\":true}}"
```

운영 인력 배정 스모크:

```powershell
curl.exe -X POST "http://localhost:8080/api/workforce/allocations" `
  -H "Content-Type: application/json" `
  -d "{\"eventId\":\"WF-001\",\"idempotencyKey\":\"WF-001\",\"sourceService\":\"ArchiveOS\",\"targetService\":\"Archive-Nexus\",\"eventType\":\"WORKFORCE_ALLOCATION_ASSIGNED\",\"role\":\"PRODUCTION_OPERATOR\",\"allocatedHeadcount\":5,\"capacityPerPersonPerDay\":20,\"productivityScore\":0.9,\"wagePerDay\":120000,\"workdayId\":\"WD-20260710\",\"simulationRunId\":\"SIM-001\",\"settlementCycleId\":\"CYCLE-001\",\"correlationId\":\"CORR-WF-001\",\"causationId\":\"CAUSE-WF-001\",\"hopCount\":0,\"maxHop\":8,\"reason\":\"Synthetic production capacity allocation\"}"

curl.exe "http://localhost:8080/api/workforce/summary"
```

## 검증 명령

```powershell
cd backend
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat bootJar --no-daemon --console=plain
cd ..
docker compose config --quiet
```

## 운영 원칙

- `dryRun=true`는 외부 HTTP 호출 없이 라우팅 결과만 확인합니다.
- 비활성화된 연동은 실제 발행하지 않고 `SKIPPED` 또는 dry-run 성격의 결과로 반환합니다.
- 하위 서비스 장애는 `retry_count`, `last_error`, `last_publish_target`, `last_publish_attempt_at`에 남깁니다.
- `hopCount > maxHop` 이벤트는 순환 구조 방지를 위해 reject/ignored 처리합니다.
- `eventId`와 `idempotencyKey`로 중복 처리를 방지합니다.
- Market-origin 이벤트는 다시 Archive-Market으로 발행하지 않습니다.
- Workforce 이벤트는 다시 운영 인력 배정을 무한 생성하지 않습니다.
- Logistics/Ledger/ArchiveOS 장애는 Nexus 제조 API 장애로 전파하지 않습니다.

## 다국어 UI

프론트엔드는 우측 상단 지구본 메뉴에서 다음 언어를 지원합니다.

- 한국어 (`ko`, 기본값)
- 영어 (`en`)
- 일본어 (`ja`)
- 중국어 간체 (`zh-CN`)

선택 언어는 `localStorage`의 `archive.locale`에 저장됩니다. API path, eventType, enum, ID, repository명, ArchiveOS/Archive-Nexus/Archive-Logistics/Archive-Ledger 같은 고유명사는 번역하지 않고 UI label만 번역합니다.

## 문서

- [아키텍처](docs/architecture.md)
- [API 참고 문서](docs/api-reference.md)
- [ArchiveOS Live Flow 계약](docs/archiveos-live-flow-contract.md)
- [Runtime Event 계약](docs/runtime-event-contract.md)
- [Nexus Runtime Event 계약](docs/nexus-runtime-event-contract.md)
- [Operations Summary 계약](docs/operations-summary-contract.md)
- [Nexus Workforce Capacity 계약](docs/nexus-workforce-capacity-contract.md)
- [Outbox 라우팅](docs/outbox-routing.md)
- [Archive-Market 연동 계약](docs/market-integration-contract.md)
- [운영 인력 모델](docs/operational-workforce.md)
- [Nexus 운영 인력 모델](docs/nexus-workforce-model.md)
- [Nexus 생산성 모델](docs/nexus-productivity-model.md)
- [운영 인력 이벤트 계약](docs/workforce-event-contract.md)
- [Archive-Logistics 계약](docs/nexus-logitics-contract.md)
- [Archive-Ledger 계약](docs/nexus-ledger-contract.md)
- [물류 일일 정산 수신함](docs/logistics-daily-settlement-inbox.md)
- [스모크 테스트](docs/smoke-test.md)
- [운영 런북](docs/operations-runbook.md)

## 기술 스택

- Java 21
- Spring Boot 3
- Spring Web / Validation / Actuator
- Spring Data JPA
- PostgreSQL
- Flyway
- Prometheus / Grafana
- Docker Compose
- React 프론트엔드

## 데이터 안전 기준

- 실제 개인정보, 실제 금융정보, 실제 결제정보, 실제 배송주소, 실제 직원/급여 데이터를 사용하지 않습니다.
- 고객 ID, 계정 ID, 카드 token, 운영 인력, 금액, 주문, 정산 값은 모두 synthetic/demo 식별자와 금액입니다.
- `.env`, token, webhook, private key, API key, local DB/data/build output은 Git에 커밋하지 않습니다.
