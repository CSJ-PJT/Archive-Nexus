# Workforce Event Contract

Archive-Nexus accepts synthetic workforce allocation events from ArchiveOS or Archive-Market.

## Allocate Workforce

```http
POST /api/workforce/allocations
```

```json
{
  "eventId": "WF-EVT-001",
  "idempotencyKey": "WF-IDEMP-001",
  "sourceService": "ArchiveOS",
  "eventType": "WORKFORCE_ALLOCATION_ASSIGNED",
  "role": "PRODUCTION_OPERATOR",
  "allocatedHeadcount": 10,
  "capacityPerPersonPerDay": 20,
  "productivityScore": 1.2,
  "wagePerDay": 150000,
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

## Supported Roles

- `PRODUCTION_OPERATOR`
- `QUALITY_INSPECTOR`
- `MAINTENANCE_ENGINEER`
- `MATERIAL_HANDLER`
- `FACTORY_MANAGER`

## Supported Sources

- `ArchiveOS`
- `Archive-Market`

## Response Fields

- `allocationId`
- `allocatedHeadcount`
- `capacityPerPersonPerDay`
- `productivityScore`
- `wagePerDay`
- `effectiveCapacity`
- `usedCapacity`
- `remainingCapacity`
- `duplicate`

## Safety

- `eventId` and `idempotencyKey` prevent duplicate writes.
- `workdayId + role` is treated as a duplicate active allocation.
- `hopCount > maxHop` returns `REJECTED`.
- Allocation data must be synthetic only.
