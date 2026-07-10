# Codex Handoff · Settlement Agency Profit + Bankruptcy Prevention Game

Audience: Archive-Nexus Codex thread

Archive-Ledger has a local synthetic game/simulation API for settlement-agency profit and bankruptcy prevention.

All data is Synthetic Data / Demo Data. Do not use real user, financial, card, account, logistics, shipment, or map data.

## Ledger game API

```http
GET  http://localhost:18080/api/game/settlement-agency/preset
POST http://localhost:18080/api/game/settlement-agency/simulate
```

## Nexus role in the game

Nexus contributes:

- manufacturing production revenue
- material cost
- maintenance cost
- quality loss cost
- logistics service fee paid to Archive-Logistics
- daily settlement agency fee paid to Archive-Logistics or Archive-Ledger

Nexus should keep game/simulation events separate from operational outbox events.

Use a namespace such as:

```text
GAME_NEXUS_PRODUCTION_PROFIT
GAME_NEXUS_LOGISTICS_FEE_PAID
GAME_NEXUS_BANKRUPTCY_RISK_SIGNAL
```

Every game event must include:

- `simulationRunId`
- `settlementCycleId`
- `tickId`
- `day`
- `correlationId`
- `maxHop`

Nexus agents should propose actions only. Real writes must go through ArchiveOS safe-mode / approval / user decision.

## Ledger files to inspect

```text
C:\Users\dan18\Documents\ArchivePJT\Archive-Ledger\docs\settlement-agency-bankruptcy-game.md
```
