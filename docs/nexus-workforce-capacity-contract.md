# Nexus Workforce Capacity Contract

Archive-Nexus는 ArchiveOS Workforce 생태계가 읽을 수 있도록 production capacity, used capacity, backlog, productivity를 read-only API로 제공한다.

## API

```http
GET /api/workforce/summary
GET /api/productivity/summary
GET /api/capacity/summary
GET /api/operations/summary
```

## Workforce summary 핵심 필드

| 필드 | 설명 |
| --- | --- |
| `enabled` | workforce 기능 활성 여부 |
| `baselineCapacity` | disabled 또는 allocation 부재 시 기준 capacity |
| `totalActiveWorkers` | 활성 합성 인력 수 |
| `dailyLaborCostKrw` | 합성 일 인건비 |
| `estimatedDailyCapacity` | 일일 예상 처리 능력 |
| `usedCapacity` | 이미 소모한 capacity |
| `remainingCapacity` | 남은 capacity |
| `backlog` | 현재 운영 윈도우 기준 미처리 물량 |
| `bottleneckRole` | 현재 운영 윈도우 기준 병목 role |
| `productivityRate` | 최신 생산성 |

## Capacity 반영 규칙

- Market `PRODUCTION_REQUESTED` 수신 시 `PRODUCTION_OPERATOR` capacity를 소모한다.
- 요청량이 available capacity 이하이면 `PRODUCTION_COMPLETED` Outbox 이벤트가 생성된다.
- 요청량이 available capacity를 초과하면 초과분은 backlog로 계산되고 `BACKLOG_INCREASED` Outbox 이벤트가 생성된다.
- workday 집계에서 backlog가 있으면 Live Flow projection으로 `CAPACITY_SHORTAGE_DETECTED`가 노출된다.
- `QUALITY_INSPECTOR`, `MAINTENANCE_ENGINEER`, `MATERIAL_HANDLER` capacity는 quality/maintenance/material bottleneck 분석에 사용한다.

## Runtime summary demand window

`/api/workforce/summary`, `/api/productivity/summary`, `/api/capacity/summary`, `/api/operations/summary`는 ArchiveOS Live Flow가 현재 운영 상태를 읽기 위한 값이다. 따라서 backlog와 bottleneck은 전체 누적 제조 이력 row 수가 아니라 현재 workday에서 처리 가능한 bounded synthetic demand 기준으로 계산한다.

- 원본 누적 production/inspection/maintenance 개수는 workday evidence에 `rawProductionDemand`, `rawQualityDemand`, `rawMaintenanceDemand`로 남긴다.
- summary 계산은 role별 capacity와 `archive.workforce.summary-demand-multiplier`를 사용해 현재 운영 윈도우를 만든다.
- role별 summary demand는 `archive.workforce.max-summary-demand-per-role`을 넘지 않는다.
- 기본값은 `summary-demand-multiplier=2`, `max-summary-demand-per-role=1000`이다.
- 이 보정은 누적 synthetic runtime data를 삭제하지 않는다. ArchiveOS 화면의 병목/미처리 물량이 전체 이력 누계 때문에 과대 표시되지 않도록 현재 운영 관제용 projection만 bounded 처리한다.

## Operations summary 추가 필드

`GET /api/operations/summary`는 다음 top-level 필드를 제공한다.

| 필드 | 설명 |
| --- | --- |
| `productionRequested` | 최신 workday 생산 요청량 |
| `productionCompleted` | 최신 workday 생산 완료량 |
| `productionBacklog` | 최신 workday 생산 backlog |
| `qualityDefects` | 최신 workday 품질 결함 수 |
| `marketOriginEvents` | Nexus가 수신한 Market-origin 이벤트 수 |
| `latestEventAt` | 최신 runtime event 발생 시각 |
| `liveFlowAvailable` | Live Flow API 사용 가능 여부. 항상 read-only 기준 `true` |

`workforce` 객체에는 다음을 포함한다.

- `totalHeadcount`
- `effectiveCapacity`
- `usedCapacity`
- `backlog`
- `productivityRate`
- `bottleneckRole`

## 안전장치

- 같은 `idempotencyKey`는 중복 처리하지 않는다.
- `hopCount > maxHop`인 외부 이벤트는 reject한다.
- Workforce event가 다시 workforce allocation을 무한 생성하지 않는다.
- 실제 직원 이름, 급여 명세, 개인정보는 저장하지 않는다.
- 인건비와 생산성은 synthetic 운영 지표다.
