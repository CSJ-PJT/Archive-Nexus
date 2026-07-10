# Operational Workforce

Archive-Nexus supports a synthetic workforce model for Manufacturing AX operations. The model is operational data only: it does not contain real employee names, salary records, personal data, or real payroll data.

## Purpose

The workforce model lets ArchiveOS or Archive-Market assign synthetic capacity to Nexus manufacturing work. Nexus then exposes capacity, backlog, labor cost, and productivity summaries without changing existing simulator behavior when the feature is disabled.

## Configuration

```env
ARCHIVE_WORKFORCE_ENABLED=false
ARCHIVE_WORKFORCE_BASELINE_CAPACITY=120
```

- `ARCHIVE_WORKFORCE_ENABLED=false`: Nexus uses baseline capacity and preserves existing behavior.
- `ARCHIVE_WORKFORCE_ENABLED=true`: active workforce allocations determine production, quality, and maintenance capacity.

## APIs

```http
GET /api/workforce/summary
GET /api/productivity/summary
GET /api/capacity/summary
POST /api/workforce/allocations
POST /api/workforce/workday/run?date=YYYY-MM-DD
```

## Allocation Contract

`POST /api/workforce/allocations`

```json
{
  "eventId": "WF-EVT-001",
  "idempotencyKey": "WF-IDEMP-001",
  "sourceService": "ArchiveOS",
  "eventType": "WORKFORCE_ALLOCATION_ASSIGNED",
  "role": "PRODUCTION",
  "assignedUnits": 10,
  "skillLevel": 1.2,
  "costPerUnitKrw": 150000,
  "workdayId": "WD-2026-07-10",
  "simulationRunId": "SIM-001",
  "settlementCycleId": "CYCLE-001",
  "correlationId": "CORR-001",
  "causationId": "CAUSE-001",
  "hopCount": 0,
  "maxHop": 8,
  "payload": {
    "synthetic": true
  }
}
```

Allowed `sourceService` values:

- `Archive-Market`
- `ArchiveOS`

Supported roles:

- `PRODUCTION`
- `QUALITY`
- `MAINTENANCE`

## Guard Rules

- Duplicate `eventId` or `idempotencyKey` returns `duplicate=true` and does not create another active allocation.
- `hopCount > maxHop` stores a rejected allocation with reason `hopCount exceeds maxHop`.
- Workforce events do not publish back to Archive-Market.
- Workforce APIs do not mutate factory source data.

## Capacity Formula

When workforce is enabled:

- `PRODUCTION` capacity = `assignedUnits * skillLevel * 12`
- `QUALITY` capacity = `assignedUnits * skillLevel * 8`
- `MAINTENANCE` capacity = `assignedUnits * skillLevel * 6`

When workforce is disabled:

- production capacity uses `ARCHIVE_WORKFORCE_BASELINE_CAPACITY`
- quality capacity uses one third of baseline
- maintenance capacity uses one fourth of baseline

## Workday Run

`POST /api/workforce/workday/run?date=YYYY-MM-DD` records one synthetic daily productivity snapshot:

- total capacity
- processed units
- backlog before and after
- synthetic KRW labor cost
- productivity rate
- bottleneck role

The same date is idempotent through `workdayId = NEXUS-WORKDAY-{date}`.
