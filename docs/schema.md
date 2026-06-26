# Archive Nexus Schema

Archive Nexus keeps the existing domain schema for factory, production, quality, inventory, logistics, maintenance, RPA, batch, and ArchiveOS interaction data.

Schema changes are managed by Flyway migrations under `backend/src/main/resources/db/migration`. `V1__initialize_archive_nexus_schema.sql` is idempotent so it supports both a new database and a pre-Flyway local volume. For an existing non-empty schema, Flyway records baseline version `0` and then applies V1 without deleting data.

For simulator runtime recovery, the operational persistence table is `simulator_state`.

## simulator_state

`simulator_state` stores one current runtime snapshot with id `archive-nexus-runtime`.

| Column | Purpose |
|---|---|
| `id` | Snapshot key |
| `running` | Simulator running flag |
| `tick` | Current simulator tick |
| `last_parallel_worker_count` | Factory workers used by the last tick |
| `factories_json` | Factory state |
| `sensor_metrics_json` | Sensor metrics |
| `production_orders_json` | Production state |
| `lots_json` | Lot state |
| `quality_inspections_json` | Quality state |
| `inventory_items_json` | Inventory balances |
| `inventory_transactions_json` | Inventory transaction history |
| `logistics_shipments_json` | Logistics state |
| `maintenance_events_json` | Maintenance state |
| `alerts_json` | Factory alerts |
| `rpa_tasks_json` | RPA tasks |
| `batch_snapshots_json` | Batch snapshots |
| `archiveos_interactions_json` | ArchiveOS adapter interaction log |
| `saved_at` | Last DB save timestamp |

The file snapshot at `data/archive-nexus-state.json` remains as fallback/local backup. Restore order is PostgreSQL first, file snapshot second, seed data last.
