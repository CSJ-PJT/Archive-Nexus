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

## economy 필드

현재 Nexus는 실제 매출/결제 데이터를 보유하지 않는다. `economy`는 synthetic workforce cost 기준의 운영 비용만 노출한다.

- `revenue`: 현재 Nexus 내부에서 확정한 synthetic revenue가 없으면 `0`
- `cost`: workforce payroll cost
- `profit`: `revenue - cost`
- `status`: 계산 범위 설명

## 호환성

기존 API 계약은 변경하지 않는다. 이 API는 ArchiveOS Live Flow / Operational Twin을 위한 신규 read-only 계약이다.
