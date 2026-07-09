# Operations Runbook

This runbook covers common local and demo operations for Archive-Nexus.

## Check Service State

```powershell
docker compose ps
curl.exe "http://localhost:8080/actuator/health"
curl.exe "http://localhost:8080/api/integrations/summary"
curl.exe "http://localhost:8080/api/outbox/summary"
```

## Archive-Logistics or Archive-Ledger Is Down

Expected behavior:

- Nexus manufacturing APIs stay available.
- `/api/integrations/summary` reports the target as `DISABLED` or `UNAVAILABLE`.
- outbox publish is skipped, dry-run, pending retry, or failed by target.
- scheduled `AUTO` publishing resumes after the target recovers.

Actions:

1. Use `dryRun=true` while diagnosing if you need to avoid external calls.
2. Inspect failed and retrying events.
3. Restart the target service outside the Nexus compose project.
4. Re-run target-specific publish after recovery.

```powershell
curl.exe "http://localhost:8080/api/outbox/events?status=PENDING_RETRY"
curl.exe "http://localhost:8080/api/outbox/events?status=FAILED"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=logitics"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=ledger"
```

## ArchiveOS Is Down

Expected behavior:

- `/api/archiveos/status` reports `DEGRADED` or `UNAVAILABLE`.
- Nexus manufacturing data APIs continue to respond.
- dashboard displays the integration state instead of blocking indefinitely.

Actions:

```powershell
curl.exe "http://localhost:8080/api/archiveos/status"
curl.exe "http://localhost:8080/api/dashboard/summary"
```

If the ArchiveOS URL changed, set `ARCHIVEOS_BASE_URL` locally and restart backend.

## Simulator Start/Stop

```powershell
curl.exe -X POST "http://localhost:8080/api/simulator/start"
curl.exe "http://localhost:8080/api/simulator/status"
curl.exe -X POST "http://localhost:8080/api/simulator/stop"
curl.exe "http://localhost:8080/api/simulator/status"
```

Final demo state should be `running:false` unless the demo explicitly requires active simulation.

## Retry Policy

Outbox events store:

- `retry_count`
- `last_error`
- `last_publish_target`
- `last_publish_attempt_at`
- `routing_status`

If retry count reaches the configured maximum, the event moves to `FAILED`. Investigate `last_error`, target health, and contract compatibility before retrying manually.

## Automatic Pipeline Settings

Docker/local defaults are intended to run the full Archive pipeline:

```env
ARCHIVE_INTEGRATIONS_LOGITICS_ENABLED=true
ARCHIVE_INTEGRATIONS_LEDGER_ENABLED=true
ARCHIVE_INTEGRATIONS_ROUTING_MODE=AUTO
ARCHIVE_INTEGRATIONS_ROUTING_PUBLISH_INTERVAL_MS=15000
ARCHIVE_INTEGRATIONS_LOGITICS_TIMEOUT_MS=30000
ARCHIVE_INTEGRATIONS_LEDGER_TIMEOUT_MS=30000
SPRING_TASK_SCHEDULING_POOL_SIZE=4
```

The scheduler pool is intentionally larger than one because simulator persistence and outbox publishing are both scheduled tasks. A single scheduler thread can delay publish when persistence takes longer than expected.

Use `false` only for fault-isolation drills or when the target service is intentionally offline.

## Safety Rules

- Do not commit `.env`, tokens, webhooks, private keys, or local data.
- Do not put Archive-Logistics or Archive-Ledger services inside the Nexus compose file.
- Use synthetic data only.
- Use `dryRun=true` before actual publish in manual high-risk operations.
