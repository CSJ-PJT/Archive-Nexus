<p align="center">
  <img src="docs/brand/archive-nexus-lockup.svg" width="760" alt="Archive Nexus" />
</p>

# Archive-Nexus

> **AI 기반 가상 공장과 지능형 RPA를 위한 제조 AX 플랫폼**

Archive-Nexus는 ArchiveOS 위에서 동작하는 제조 AX 애플리케이션입니다.

여러 가상 공장, 재고 시스템, 물류 시스템, 품질 시스템, 정비 시스템을 하나의 산업 생태계로 시뮬레이션하고, ArchiveOS의 AI Runtime과 지능형 RPA를 통해 이상 감지, 원인 분석, 승인 기반 조치를 수행하는 것을 목표로 합니다.

---

## Concept

Archive-Nexus는 실제 공장을 직접 제어하는 시스템이 아닙니다.

대신 여러 개의 가상 공장을 생성하고, 각 공장에서 생산·설비·품질·재고·물류 데이터를 지속적으로 발생시키는 **제조 디지털 트윈 시뮬레이션 플랫폼**입니다.

생성된 데이터는 완전한 무작위 값이 아니라, 원인과 결과가 연결된 규칙 기반 데이터입니다.

예를 들어:

```text
베어링 마모
→ 진동 증가
→ 온도 상승
→ 전류 증가
→ 생산량 감소
→ 불량률 증가
→ AI 원인 분석
→ 정비 작업 추천
→ 승인 기반 RPA 실행
```

Archive-Nexus는 이렇게 생성된 산업 데이터를 ArchiveOS와 연동하여 분석하고, 필요한 경우에만 AI와 RPA가 개입하도록 설계합니다.

---

## Relationship with ArchiveOS

ArchiveOS는 플랫폼입니다.

Archive-Nexus는 ArchiveOS 위에서 실행되는 산업 애플리케이션입니다.

```text
ArchiveOS
├── AI Runtime
├── Spring AI
├── Spring Batch
├── RAG Engine
├── Intelligent RPA
├── Workflow Engine
├── Approval Gate
└── Observability
        │
        ▼
Archive-Nexus
├── Virtual Factories
├── Inventory Hub
├── Logistics Hub
├── Quality System
├── Maintenance System
└── Factory Simulator
```

역할은 다음과 같이 분리합니다.

| 구분 | 역할 |
|---|---|
| ArchiveOS | AI 실행, RPA 승인, Batch, RAG, 관제, 워크플로우 |
| Archive-Nexus | 공장, 생산, 품질, 재고, 물류, 정비 도메인 시뮬레이션 |

ArchiveOS는 제조 도메인 로직을 직접 알지 않습니다.

Archive-Nexus는 ArchiveOS의 API 또는 SDK를 통해 AI 분석, RPA 작업 생성, 승인 요청, 이벤트 전송을 수행합니다.

---

## Core Goals

- 여러 가상 공장을 병렬로 운영한다.
- 각 공장은 난수와 규칙 기반으로 생산·설비·품질·재고 데이터를 자동 생성한다.
- Codex나 외부 AI의 개입 없이도 공장 생태계가 계속 돌아가야 한다.
- AI 개입은 이상 감지, 원인 분석, 조치 추천, 승인 요청이 필요한 경우에만 발생한다.
- ArchiveOS는 중앙 관제 플랫폼으로 사용한다.
- Archive-Nexus는 ArchiveOS 위에서 실행되는 제조 AX 애플리케이션으로 분리한다.

---

## Initial Factory Scenario

초기 MVP에서는 3개의 가상 공장을 구성합니다.

### Factory A

자동차 부품 생산 공장입니다.

- 정상 생산 위주
- 낮은 불량률
- 안정적인 생산량
- 일일 생산 리포트 중심

### Factory B

배터리 모듈 생산 공장입니다.

- 설비 이상 이벤트가 자주 발생
- 진동, 온도, 전류 데이터가 중요
- 예지보전 시나리오 중심

### Factory C

전장 부품 생산 공장입니다.

- 품질 불량률 변동
- Lot 기반 품질 추적
- 출하 보류 및 재작업 시나리오 중심

---

## Domain Modules

Archive-Nexus는 다음 도메인으로 구성됩니다.

### Factory

- 공장 정보
- 생산 라인
- 설비
- 작업 지시
- 생산 실적

### Production

- 생산 주문
- 작업 지시
- Lot
- 생산량
- 라인 가동률
- 생산 지연

### Inventory

- 원자재 재고
- 반제품 재고
- 완제품 재고
- 안전재고
- 입고
- 출고
- 재고 부족 이벤트

### Logistics

- 공장 간 자재 이동
- 창고 출하
- 배송 상태
- 납기 지연
- 출하 우선순위

### Quality

- Lot별 품질 검사
- 불량 이벤트
- 불량률 추이
- 출하 보류
- 재작업 대상

### Maintenance

- 설비 상태
- 센서 이상
- 정비 이력
- 예지보전 후보
- 고장 위험도

### RPA

- 이상 이벤트 감지
- AI 분석 요청
- 조치 추천
- 승인 대기
- 실행 로그
- 재시도 및 실패 관리

---

## Data Generation Model

Archive-Nexus의 데이터는 단순 랜덤값이 아니라, 제조 현장에서 발생할 수 있는 관계형 이벤트를 기반으로 생성됩니다.

### Normal Pattern

```text
생산량 ±5%
불량률 0.3% ~ 1.2%
온도 60℃ ~ 75℃
진동 0.1 ~ 0.4
전류 8A ~ 12A
라인 가동률 90% 이상
```

### Abnormal Pattern

```text
진동 0.7 이상
온도 85℃ 이상
전류 급증
생산량 20% 이상 감소
불량률 3% 이상
재고 안전선 이하
납기 지연 발생
```

### Example Causal Scenario

```text
원자재 부족
→ 생산 대기
→ 생산량 감소
→ 납기 지연
→ AI 원인 분석
→ 긴급 발주 추천
→ 승인 요청
→ RPA 작업 생성
```

---

## Intelligent RPA Intervention

일반 데이터 생성과 정상 배치 처리는 자동으로 수행합니다.

AI와 RPA는 다음 상황에서만 개입합니다.

- 불량률 급증
- 설비 진동 또는 온도 임계치 초과
- 생산량 급감
- 재고 부족
- 납기 지연
- 반복 장애
- 정비 미처리
- 출하 보류
- 에너지 피크
- 수동 승인이 필요한 조치

RPA Task는 다음 상태를 가집니다.

```text
detected
→ analyzing
→ recommendation_ready
→ approval_required
→ approved / rejected
→ executing
→ completed / failed
```

---

## Planned Architecture

```text
archive-nexus
├── backend
│   ├── Spring Boot
│   ├── Spring Data JPA
│   ├── PostgreSQL
│   ├── Simulator Service
│   ├── Factory Domain
│   ├── Inventory Domain
│   ├── Logistics Domain
│   ├── Quality Domain
│   ├── Maintenance Domain
│   └── ArchiveOS Adapter
│
├── frontend
│   ├── React
│   ├── Vite
│   ├── TypeScript
│   ├── Overview
│   ├── Factories
│   ├── Inventory
│   ├── Quality
│   ├── Maintenance
│   ├── Logistics
│   └── RPA
│
├── simulator
│   └── Factory Data Generator
│
├── docs
│   ├── architecture.md
│   ├── schema.md
│   ├── simulation-model.md
│   ├── factory-scenarios.md
│   └── archiveos-integration.md
│
└── docker-compose.yml
```

---

## Planned API

### Factory

```http
GET /api/factories
GET /api/factories/{factoryId}
GET /api/factories/{factoryId}/lines
GET /api/factories/{factoryId}/metrics
GET /api/factories/{factoryId}/alerts
```

### Production

```http
GET /api/production/orders
GET /api/production/work-orders
GET /api/production/lots
```

### Inventory

```http
GET /api/inventory/items
GET /api/inventory/transactions
GET /api/inventory/alerts
```

### Logistics

```http
GET /api/logistics/shipments
GET /api/logistics/delays
```

### Quality

```http
GET /api/quality/inspections
GET /api/quality/defects
```

### Maintenance

```http
GET /api/maintenance/events
GET /api/maintenance/risks
```

### RPA

```http
GET /api/rpa/tasks
GET /api/rpa/tasks/{id}
POST /api/rpa/tasks/{id}/approve
POST /api/rpa/tasks/{id}/reject
```

### ArchiveOS / Batch

```http
GET /api/archiveos/interactions
GET /api/batch/snapshots
```

### Simulator

```http
POST /api/simulator/start
POST /api/simulator/stop
GET /api/simulator/status
GET /api/simulator/persistence
```

`GET /api/simulator/status`는 마지막 tick에서 병렬 실행된 factory worker 수를 `parallelWorkerCount`로 반환합니다.
`GET /api/simulator/persistence`는 `storageMode`, `dbAvailable`, `fileSnapshotAvailable`, `lastSavedAt`, `restoredFrom`을 반환합니다.

시뮬레이터 운영 상태는 PostgreSQL/JPA 저장소를 우선 사용합니다. `data/archive-nexus-state.json` 파일 snapshot은 DB 저장 실패나 DB 상태 부재 시 fallback/local backup 용도로만 사용합니다.

---

## Operations Dashboard

React 운영 대시보드는 5초마다 제조 도메인 API를 병렬 조회하여 하나의 관제 화면으로 구성합니다.

전용 운영 요약은 `GET /api/dashboard/summary`에서 simulator, persistence, 제조 domain count, anomaly, 최근 anomaly와 Manufacturing AI 요약을 함께 반환합니다.

- Overview: 시뮬레이터 상태, 생산 달성률, 평균 불량률, 재고 위험, 출하 지연, 최근 이상 이벤트
- Factories: 공장별 생산·품질·물류·정비·경보 상태
- Production / Inventory / Quality / Maintenance / Logistics: 실제 시뮬레이터 데이터 테이블
- RPA: 전체 작업 이력과 `APPROVAL_REQUIRED` 작업 승인·반려
- Settings: PostgreSQL/파일 백업 상태, Batch snapshot, ArchiveOS 상호작용 이력

API 일부가 실패하면 마지막 정상 데이터를 유지하고 상단 오류 영역에 실패 상태를 표시합니다. 수동 새로고침과 simulator start/stop 작업도 동일한 오류 처리 경로를 사용합니다. 상세 계약은 [`docs/operations-dashboard.md`](docs/operations-dashboard.md)를 참고합니다.

PostgreSQL schema는 Flyway로 관리합니다. 기존 로컬 볼륨도 삭제하지 않고 baseline version `0` 이후 최신 migration을 적용하며, Docker Compose backend는 PostgreSQL healthcheck 통과 후 시작합니다. 프론트엔드는 Nginx의 동일 출처 `/api` reverse proxy를 통해 backend에 접근합니다.

---

## Monitoring

Archive Nexus는 Spring Boot Actuator와 Micrometer Prometheus registry를 통해 시뮬레이터 및 영속화 상태를 노출합니다. Docker Compose는 Prometheus와 provisioning된 Grafana 대시보드를 함께 실행합니다.

```powershell
docker compose up --build -d
```

| 화면/Endpoint | 주소 |
|---|---|
| Archive Nexus | <http://localhost:15173> |
| Backend health | <http://localhost:8080/actuator/health> |
| Prometheus metrics | <http://localhost:8080/actuator/prometheus> |
| Prometheus | <http://localhost:19090> |
| Grafana | <http://localhost:13000> |

Grafana 기본 계정은 `admin / archive_nexus_local`이며 `.env`의 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`로 변경할 수 있습니다. `Archive Nexus / Archive Nexus Operations` 대시보드는 simulator, factory, anomaly, RPA, batch, PostgreSQL 저장 및 복구 원본 지표를 5초 주기로 표시합니다.

상세 지표와 운영 확인 절차는 [`docs/monitoring.md`](docs/monitoring.md)를 참고합니다.

---

## Manufacturing Multi-Agent

Archive Nexus는 Spring AI `ChatModel` 계약을 사용하는 Manufacturing Orchestrator를 제공합니다. 모델 bean이 없거나 호출에 실패하면 동일 API가 keyword/rule router로 동작하므로 API key 없이도 로컬·테스트 환경에서 사용할 수 있습니다.

지원 Agent:

- `ProductionAgent`: 목표 대비 생산 달성률, 최근 생산 저하, 공장 간 차이와 병목 근거
- `QualityAgent`: 평균/최근 불량률, 품질 경보와 Lot 이상 근거
- `MaintenanceAgent`: 온도·진동·전류 임계치, 정비 이벤트와 설비 위험 근거

```http
POST /api/ai/query
GET /api/ai/queries
GET /api/ai/queries/{queryId}
GET /api/ai/summary
```

```json
{
  "question": "3공장의 생산량 감소 원인과 설비 이상 여부를 분석해줘",
  "factoryId": "FACTORY-C",
  "requestedBy": "operator"
}
```

복합 질문은 최대 3개의 제한된 worker에서 관련 Agent를 병렬 실행합니다. 한 Agent가 실패해도 성공 결과로 부분 응답을 구성하며, 조치가 필요한 분석은 제조 데이터를 직접 변경하지 않고 기존 RPA/승인 흐름으로 전달합니다. 실행 이력은 PostgreSQL `ai_query_history`에 저장되고 모든 단계는 ArchiveOS Interaction Log에 기록됩니다.

프론트엔드의 `Manufacturing AI` 메뉴에서 질문, 공장, Intent routing, Agent별 결과, 근거, 권장 조치, RPA 생성 여부와 Query History를 확인할 수 있습니다. 상세 설계는 [`docs/multi-agent-architecture.md`](docs/multi-agent-architecture.md)를 참고합니다.

---

## Planned Tech Stack

### Backend

- Java 21
- Spring Boot 3
- Spring Data JPA
- PostgreSQL
- Gradle

### Frontend

- React
- Vite
- TypeScript

### Infrastructure

- Docker
- Docker Compose
- PostgreSQL

### Integration

- ArchiveOS API
- ArchiveOS SDK
- REST API
- Webhook
- RPA Event Adapter

---

## MVP Scope

구현 범위는 다음과 같습니다.

- Factory A/B/C 기본 데이터 모델
- PostgreSQL schema
- 가상 공장 seed data
- 센서 및 생산 데이터 생성기
- 재고 입출고 시뮬레이션
- 품질 검사 시뮬레이션
- 설비 이상 이벤트 생성
- RPA Task 생성 조건
- React 기반 운영 화면
- ArchiveOS mock adapter
- Docker Compose 실행 환경

---

## Roadmap

- [x] 프로젝트 기본 구조 생성
- [x] Spring Boot backend 생성
- [x] React frontend 생성
- [x] PostgreSQL schema 작성
- [x] Factory A/B/C seed data 생성
- [x] simulator start/stop 구현
- [x] 생산 데이터 자동 생성
- [x] 재고 시스템 구현
- [x] 품질 검사 시스템 구현
- [x] 설비 이상 이벤트 구현
- [x] RPA task 생성 로직 구현
- [x] ArchiveOS mock adapter 구현
- [ ] ArchiveOS 실제 연동
- [x] Docker Compose 실행 환경 구성
- [x] 제조 운영 대시보드 구성
- [x] Prometheus/Grafana 운영 모니터링 기반 구성
- [x] Spring AI 제조 Orchestrator와 전문 Agent 기반 구성
- [ ] 감사 로그 고도화
- [ ] 사용자 인증 및 역할 기반 권한 구성
- [ ] 화면 캡처 및 운영 문서 작성

---

## Slogan

> **가상의 공장을 통해 실제 AX 운영 구조를 검증한다.**

> **Virtual Factories. Real Automation.**

---

## License

라이선스 정책은 프로젝트 운영 방침에 따라 추후 정의합니다.
