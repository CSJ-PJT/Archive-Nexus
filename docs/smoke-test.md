# Smoke Test

Use this checklist after changing routing, compose, environment variables, or dashboard integration.

## Backend Build

```powershell
cd backend
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat bootJar --no-daemon --console=plain
cd ..
```

## Compose Validation

```powershell
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

Expected:

- `archive-nexus-backend` healthy
- `archive-nexus-frontend` healthy
- PostgreSQL healthy
- Prometheus and Grafana reachable when enabled

## API Smoke

```powershell
curl.exe "http://localhost:8080/actuator/health"
curl.exe "http://localhost:8080/api/outbox/summary"
curl.exe "http://localhost:8080/api/integrations/summary"
```

Expected:

- backend health is `UP`
- Nexus status is `HEALTHY`
- disabled integrations are reported as `DISABLED`

## Routing Smoke

```powershell
curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=logistics"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=auto&dryRun=true"
curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=ledger"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=ledger&dryRun=true"
```

Expected:

- logistics events route to `LOGITICS`;
- ledger events route to `LEDGER`;
- `dryRun=true` does not call external HTTP services;
- publish response includes target candidate, skipped, published, and failed counts.

## Frontend Smoke

```powershell
curl.exe -I "http://localhost:15173"
```

Expected:

- HTTP 200 response;
- dashboard can load current operating data;
- ArchiveOS degraded/unavailable status does not block dashboard rendering.

