# Nexus Workforce Model

Archive-Nexus uses synthetic workforce allocation to determine manufacturing capacity for Market-origin production requests.

No real employee names, payroll records, personal data, or real wage data are stored. All headcount, wage, and productivity values are synthetic operational simulation values.

## Roles

| Role | Default capacity |
| --- | ---: |
| `PRODUCTION_OPERATOR` | 20 production units per person per day |
| `QUALITY_INSPECTOR` | 30 inspection units per person per day |
| `MAINTENANCE_ENGINEER` | 5 maintenance cases per person per day |
| `MATERIAL_HANDLER` | 25 material handling units per person per day |
| `FACTORY_MANAGER` | 80 management capacity units per person per day |

Each allocation records:

- `allocatedHeadcount`
- `capacityPerPersonPerDay`
- `productivityScore`
- `wagePerDay`
- `effectiveCapacity`
- `usedCapacity`
- `remainingCapacity`

## Capacity Formula

```text
effectiveCapacity = allocatedHeadcount * capacityPerPersonPerDay * productivityScore
remainingCapacity = effectiveCapacity - usedCapacity
```

When `ARCHIVE_WORKFORCE_ENABLED=false`, Nexus uses baseline capacity and preserves existing behavior.

## Market Production Processing

Market `PRODUCTION_REQUESTED` events are processed by available `PRODUCTION_OPERATOR` capacity.

| Condition | Result |
| --- | --- |
| requested quantity <= available production capacity | `PRODUCTION_COMPLETED` outbox event |
| requested quantity > available production capacity | `BACKLOG_INCREASED` outbox event |

The generated outbox payload includes:

- `workdayId`
- `workforceAllocationId`
- `productivityScore`
- `usedCapacity`
- `remainingCapacity`
- `backlogCount`
- `bottleneckRole`
- `orderId`
- `correlationId`
- `simulationRunId`
- `settlementCycleId`

## Guard Rules

- duplicate `eventId` or `idempotencyKey` is duplicate-safe;
- same `workdayId + role` is duplicate-safe at service level;
- `hopCount > maxHop` is rejected;
- workforce allocation does not generate another workforce allocation.
