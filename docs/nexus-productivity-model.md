# Nexus Productivity Model

Nexus records a synthetic workday result for operational capacity review.

## Workday Result

`POST /api/workforce/workday/run?date=YYYY-MM-DD`

The API creates one idempotent result per date using:

```text
workdayId = NEXUS-WORKDAY-{date}
```

Stored fields:

- `totalCapacity`
- `usedCapacity`
- `remainingCapacity`
- `productionRequested`
- `productionCompleted`
- `productionBacklog`
- `qualityChecked`
- `qualityDefects`
- `maintenanceCompleted`
- `payrollCost`
- `productivityScore`
- `bottleneckRole`

## Productivity Score

```text
productivityScore = productionCompleted / productionRequested
```

If no production is requested, score is `1.0` because there is no unprocessed production demand.

## Bottleneck

The bottleneck role is selected by the largest capacity gap:

1. `PRODUCTION_OPERATOR`
2. `QUALITY_INSPECTOR`
3. `MAINTENANCE_ENGINEER`
4. `NONE`

## Payroll Cost

Payroll cost is synthetic KRW:

```text
payrollCost = sum(allocatedHeadcount * wagePerDay)
```

This value appears in workforce and productivity summaries. Nexus does not store real payroll or employee data.
