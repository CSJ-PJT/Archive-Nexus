# Operations Summary Contract

ArchiveOS Operational Twin은 `GET /api/operations/summary`를 통해 Archive-Nexus의 read-only 운영 상태를 수집한다.

## API

```http
GET /api/operations/summary
```

## 응답 예시

```json
{
  "serviceName": "Archive-Nexus",
  "serviceRole": "Manufacturing AX runtime, market inbound, workforce capacity, and outbox routing",
  "status": "HEALTHY",
  "latestEventAt": "2026-07-10T00:00:00Z",
  "productionRequested": 120,
  "productionCompleted": 100,
  "productionBacklog": 20,
  "qualityDefects": 3,
  "marketOriginEvents": 42,
  "outbox": {
    "pending": 12,
    "published": 80,
    "failed": 0,
    "retry": 2
  },
  "economy": {
    "revenue": 0,
    "cost": 960000,
    "profit": -960000,
    "status": "SYNTHETIC_WORKFORCE_COST_ONLY"
  },
  "workforce": {
    "totalHeadcount": 12,
    "effectiveCapacity": 240,
    "usedCapacity": 190,
    "backlog": 15,
    "productivityRate": 0.86,
    "bottleneckRole": "QUALITY_INSPECTOR"
  },
  "runtime": {
    "service": "Archive-Nexus",
    "runtimeActive": true,
    "autoRunEnabled": true,
    "schedulerStatus": "IDLE",
    "lastWorkAt": "2026-07-10T00:00:00Z",
    "lastEventAt": "2026-07-10T00:00:00Z",
    "eventsProducedLastTick": 3,
    "eventsConsumedLastTick": 3,
    "backlogCount": 15,
    "pipelineStatus": "LIVE"
  },
  "degradedReason": null,
  "liveFlowAvailable": true,
  "readOnlyApis": [
    "/api/runtime-events/recent",
    "/api/runtime-events/correlation/{correlationId}",
    "/api/runtime-events/entity/{entityId}"
  ],
  "generatedAt": "2026-07-10T00:00:00Z"
}
```

## status 산정

| 조건 | status |
| --- | --- |
| Outbox failed/retry 없음 | `HEALTHY` |
| Outbox `FAILED` 존재 | `DEGRADED` |
| Outbox `PENDING_RETRY` 존재 | `DEGRADED` |

ArchiveOS 장애 여부는 Nexus 운영 상태를 바꾸지 않는다. ArchiveOS가 down이어도 이 API는 Nexus 내부 상태를 기준으로 응답한다.

## 생산/품질/Market-origin 필드

| 필드 | 원천 |
| --- | --- |
| `productionRequested` | 최신 `nexus_workday_result.production_requested` |
| `productionCompleted` | 최신 `nexus_workday_result.production_completed` |
| `productionBacklog` | 최신 `nexus_workday_result.production_backlog`, 없으면 workforce backlog |
| `qualityDefects` | 최신 `nexus_workday_result.quality_defects` |
| `marketOriginEvents` | `nexus_market_event` 수신 건수 |
| `latestEventAt` | 최신 runtime event projection 시각 |
| `liveFlowAvailable` | Runtime event API 사용 가능 여부 |

## runtime 필드

`runtime` 객체는 Autonomous Runtime Work Loop 상태를 나타낸다.

| 필드 | 설명 |
| --- | --- |
| `runtimeActive` | runtime loop가 활성 상태인지 여부 |
| `autoRunEnabled` | 자동 tick 설정 여부 |
| `schedulerStatus` | `DISABLED`, `IDLE`, `RUNNING`, `LOCKED`, `FAILED` |
| `lastWorkAt` | 마지막 synthetic work tick 실행 시각 |
| `lastEventAt` | 마지막 synthetic runtime event 발생 시각 |
| `eventsProducedLastTick` | 마지막 tick에서 신규 생성된 synthetic event 수 |
| `eventsConsumedLastTick` | 마지막 tick에서 처리된 synthetic event 수 |
| `backlogCount` | workforce/outbox 기준 현재 backlog projection |
| `pipelineStatus` | `LIVE`, `DISABLED`, `DEGRADED` |

`productionBacklog`와 `workforce.backlog`는 ArchiveOS Live Flow용 현재 운영 projection이다. Nexus에 누적된 전체 production/inspection/maintenance 이력 row 수를 그대로 backlog로 사용하지 않는다. workday snapshot이 아직 없으면 role별 bounded synthetic demand와 capacity gap으로 계산한다.

운영 윈도우 기본값:

- `archive.workforce.summary-demand-multiplier=2`
- `archive.workforce.max-summary-demand-per-role=1000`

원시 누적 수요는 workday run evidence의 `rawProductionDemand`, `rawQualityDemand`, `rawMaintenanceDemand`에서 추적할 수 있다.

## economy 필드

현재 Nexus는 실제 매출/결제 데이터를 보유하지 않는다. `economy`는 synthetic workforce cost 기준의 운영 비용만 노출한다.

- `revenue`: 현재 Nexus 내부에서 확정한 synthetic revenue가 없으면 `0`
- `cost`: workforce payroll cost
- `profit`: `revenue - cost`
- `status`: 계산 범위 설명

## 호환성

기존 API 계약은 변경하지 않는다. 이 API는 ArchiveOS Live Flow / Operational Twin을 위한 신규 read-only 계약이다.
