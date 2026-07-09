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

Actions:

1. Keep `dryRun=true` while diagnosing.
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

## Safety Rules

- Do not commit `.env`, tokens, webhooks, private keys, or local data.
- Do not put Archive-Logistics or Archive-Ledger services inside the Nexus compose file.
- Use synthetic data only.
- Use `dryRun=true` before actual publish in manual operations.

