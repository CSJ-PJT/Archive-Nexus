<p align="center">
  <img src="docs/brand/archive-nexus-lockup.svg" width="900" alt="Archive Nexus" />
</p>

# Archive-Nexus

> **AI 기반 가상 공장과 지능형 RPA를 위한 제조 AX 플랫폼**

Archive-Nexus는 ArchiveOS 위에서 동작하는 제조 시뮬레이션 애플리케이션입니다. Factory A/B/C의 생산, 품질, 재고, 물류와 정비 데이터를 생성하고 운영 대시보드에서 상태를 관찰합니다.

## 현재 구현 상태

- Spring Boot 기반 제조 시뮬레이터
- React 기반 운영 대시보드
- 생산·재고·품질·정비·물류 도메인
- PostgreSQL 우선 저장과 JSON snapshot fallback
- RPA 작업 생성, 승인과 반려 흐름
- PostgreSQL 기반 운영 작업 생성·실행·취소·재시도와 실행 로그
- Actuator, Prometheus와 Grafana 모니터링
- Spring AI 제조 Orchestrator
- Production, Quality, Maintenance, Inventory, Logistics 전문 Agent와 Cross Domain Root Cause Agent
- ArchiveOS Workflow/PM Approval/Result Callback 양방향 계약
- Evidence, Recommendation, Confidence, CorrelationId 기반 Audit Log
- 센서 이상부터 품질·재고·출하·승인·복구까지 재현하는 Scenario Engine

사용자 인증과 역할 기반 권한, webhook 기반 즉시 승인 통지는 후속 작업입니다.

## ArchiveOS와의 관계

```text
ArchiveOS
├── AI Runtime / RAG
├── Batch / Workflow
├── RPA / Approval
└── Observability
        │
        ▼
Archive-Nexus
├── Virtual Factories
├── Production / Quality
├── Inventory / Logistics
├── Maintenance
└── Manufacturing AI
```

ArchiveOS는 공통 AI 실행 환경을 담당하고 Archive-Nexus는 제조 도메인 로직을 담당합니다.

## Platform Contract

Archive-Nexus는 Archive Suite 안에서 자신이 어떤 Industry Application인지 외부 시스템이 확인할 수 있도록
`GET /api/platform/manifest`를 제공합니다.

이 manifest는 다음 정보를 포함합니다.

- 제품군, 역할, 계약 버전, 실행 환경
- Manufacturing Simulator, Multi-Agent Orchestrator, Workflow Contract, RPA Approval Bridge 등 보유 역량
- ArchiveOS Workflow/Approval/Callback과 Nexus AI Query/Simulator API 계약 표면
- ArchiveOS 연결 상태와 Backend Atlas 참조 링크
- ArchiveOS 장애 시 제조 API 지속 응답, Agent 직접 실행 금지, evidence/recommendation/confidence 보존 같은 운영 보장

Backend Atlas는 이 manifest를 포트폴리오/학습 자료의 실제 구현 근거로 연결할 수 있고,
ArchiveOS는 Nexus를 Managed Industry Application으로 등록할 때 동일 계약 정보를 사용할 수 있습니다.

## 가상 공장

- **Factory A**: 자동차 부품, 안정적인 생산과 낮은 불량률
- **Factory B**: 배터리 모듈, 센서 이상과 예지보전
- **Factory C**: 전장 부품, Lot 품질 추적과 출하 보류

## 운영 화면

- Overview, Factories, Production, Inventory
- Quality, Maintenance, Logistics, RPA
- Manufacturing AI, Settings

운영 화면은 제조 API를 주기적으로 조회하고 일부 요청이 실패하면 마지막 정상 데이터를 유지하면서 오류 상태를 표시합니다.

## 기술 구성

- Java 21, Spring Boot 3, Spring Data JPA, Spring AI
- React, Vite, TypeScript, Nginx
- PostgreSQL, Flyway
- Docker, Docker Compose
- Actuator, Prometheus, Grafana

## 로컬 실행

```powershell
docker compose up --build -d
docker compose ps
```

| 항목 | 주소 |
| --- | --- |
| Archive Nexus | `http://localhost:15173` |
| Backend Health | `http://localhost:8080/actuator/health` |
| Prometheus | `http://localhost:19090` |
| Grafana | `http://localhost:13000` |

## 운영 작업 API

`POST /api/tasks`, `GET /api/tasks`, `GET /api/tasks/{id}`, `GET /api/tasks/{id}/logs`,
`POST /api/tasks/{id}/run`, `POST /api/tasks/{id}/sync`, `POST /api/tasks/{id}/cancel`,
`POST /api/tasks/{id}/retry`를 제공한다. 데모 시나리오는
`POST /api/scenarios/sensor-quality-inventory-logistics-recovery/run`으로 시작한다.

## Synthetic Domain Event Outbox

Archive-Nexus는 실제 금융/개인정보 없이 synthetic 제조·모빌리티 운영 이벤트를 `nexus_outbox_event`에 저장하고
Archive-Logistics 또는 Archive-Ledger로 라우팅 publish할 수 있다.

- `GET /api/outbox/events`
- `GET /api/outbox/events/{eventId}`
- `POST /api/outbox/events/generate?count=1000`
- `POST /api/outbox/events/publish`
- `GET /api/outbox/summary`

Ledger가 unavailable이면 제조 API는 계속 응답하고 outbox event는 `PENDING_RETRY` 또는 `FAILED` 상태로 남는다.
`retry_count`와 `last_error`를 통해 재처리 상태를 확인한다.

### Nexus Outbox Routing

Archive-Nexus는 synthetic outbox event를 `eventType` 기준으로 라우팅한다.

- Archive-Logistics 대상: `LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`, `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT`
- Archive-Ledger 직접 대상: `PRODUCTION_COMPLETED`, `MATERIAL_CONSUMED`, `MAINTENANCE_COMPLETED`, `QUALITY_DEFECT_DETECTED`, `EMERGENCY_PURCHASE_REQUESTED`, `QUALITY_CLAIM_CHARGED`, `CORPORATE_CARD_USED`, `VENDOR_PAYMENT_REQUESTED`
- 내부 처리/스킵 대상: `SHIPMENT_HOLD_CREATED`

기본값은 외부 연동 비활성화다. Archive-Logistics 또는 Archive-Ledger가 꺼져 있어도 Nexus는 정상 기동한다.

```env
ARCHIVE_INTEGRATIONS_LOGITICS_ENABLED=false
ARCHIVE_INTEGRATIONS_LOGITICS_BASE_URL=http://host.docker.internal:8092
ARCHIVE_INTEGRATIONS_LEDGER_ENABLED=false
ARCHIVE_INTEGRATIONS_LEDGER_BASE_URL=http://host.docker.internal:18080
ARCHIVE_INTEGRATIONS_ROUTING_ALLOW_LEDGER_DIRECT_FALLBACK_FOR_LOGISTICS=false
```

외부 서비스명은 `Archive-Logistics`로 표기한다. 다만 기존 API와 운영 설정 호환성을 위해
`LOGITICS`, `logitics`, `ARCHIVE_INTEGRATIONS_LOGITICS_*` 키는 유지한다.

```powershell
curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=100&type=logistics"
curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=100&type=ledger"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=auto&dryRun=true"
curl.exe "http://localhost:8080/api/outbox/summary"
curl.exe "http://localhost:8080/api/integrations/summary"
```

자세한 계약 문서:

- [Outbox routing](docs/outbox-routing.md)
- [Nexus to Archive-Logistics contract](docs/nexus-logitics-contract.md)
- [Nexus to Archive-Ledger contract](docs/nexus-ledger-contract.md)
- [Demo: Nexus to Logistics to Ledger](docs/demo-nexus-to-logitics-to-ledger.md)

ArchiveOS 상태는 `GET /api/archiveos/status`에서 확인한다. 기본 연동 주소는
`http://host.docker.internal:4000`이며, ArchiveOS 장애는 Nexus 제조 데이터 API와 화면 로딩을
중단시키지 않고 `DEGRADED` 또는 `UNAVAILABLE` 상태로 표시된다.
Windows Docker Desktop에서는 `host.docker.internal`이 기본 동작한다. Linux Docker Engine에서는
Compose의 `host-gateway` mapping을 사용하며, ArchiveOS 위치가 다르면 `ARCHIVEOS_BASE_URL`을 override한다.
작업은 `DRAFT → PENDING → ANALYZING → WAITING_APPROVAL → APPROVED → RUNNING → VERIFYING → SUCCESS`를
기본 흐름으로 사용하며 실패·반려·취소·재시도를 별도 상태로 기록한다. Agent 분석은 기존
Manufacturing Orchestrator와 Domain Service를 호출한다. 실패 또는 승인 필요 알림의 Discord webhook은
`ARCHIVE_NEXUS_DISCORD_WEBHOOK_URL` 환경변수로만 주입한다.

## 문서

- [운영 대시보드](docs/operations-dashboard.md)
- [모니터링](docs/monitoring.md)
- [Multi-Agent 구조](docs/multi-agent-architecture.md)
- [브랜드 가이드](docs/brand/README.md)

## Roadmap

- [x] 가상 공장과 제조 데이터 생성
- [x] PostgreSQL 저장과 snapshot fallback
- [x] 제조 운영 대시보드
- [x] RPA 승인 흐름
- [x] Prometheus/Grafana 기반
- [x] Spring AI 제조 Agent 기반
- [x] ArchiveOS Workflow/Approval/History 연동
- [x] 제조 실행 감사 로그
- [ ] 사용자 인증과 역할 기반 권한

## Brand

> **Intelligence. Automation. Convergence.**

Archive Nexus는 ArchiveOS와 동일한 공식 `A` 마크를 사용합니다. `NEXUS`는 청록색으로 구분하고 `ARCHIVE`와 충분히 떨어뜨려 `E`와 `N`이 겹치지 않도록 수정했습니다.

README, Sidebar, Header와 브라우저 아이콘은 동일한 공식 마크를 사용합니다.

Archive-Nexus는 제조·출하 이벤트를 생성하고 Outbox 라우팅 정책에 따라 물류 이벤트는 Archive-Logistics로, 정비·구매·품질·카드성 비용 이벤트는 Archive-Ledger로 전달하는 Manufacturing AX 백엔드입니다. 외부 서비스 장애가 제조 API로 전파되지 않도록 target별 retry, dry-run, routing summary, last_error를 제공합니다.

## License

라이선스 정책은 프로젝트 운영 방침에 따라 추후 정의합니다.
