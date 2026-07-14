# RC Security Baseline

Archive-Nexus processes **Synthetic Runtime Data only**. This baseline protects the Docker Compose / HTTP boundary without introducing Kafka, mTLS, or Kubernetes.

## Exposure audit

| Port or API | RC exposure | Required access | RC control |
| --- | --- | --- | --- |
| PostgreSQL 5432 | Docker private network only | backend container | `expose`, no host `ports` |
| Backend 8080 | `127.0.0.1` only | local reverse proxy / operator | service-token filter for write APIs |
| Frontend 15173 | `127.0.0.1` only | local browser | reverse proxy only |
| Prometheus 19090 / Grafana 13000 | `127.0.0.1` only | local operator | no default Grafana credential |
| `/actuator/health` | local backend health check | liveness/readiness | public health only |
| `/actuator/prometheus` | authenticated read | monitoring collector | `authenticated:read` scope |
| `/api/events/market/**` | internal write | Archive-Market | `production:ingest` |
| `/api/logistics/settlements/**` | internal write | Archive-Logistics | `logistics:ingest` |
| `/api/outbox/**` writes, simulator, workforce, task, scenario, RPA | admin write | ArchiveOS | `admin:operate` |
| raw outbox/runtime/audit/settlement/task-log reads | authenticated read | ArchiveOS reader | `authenticated:read` |

Public synthetic summaries remain read-only: `/api/operations/summary`, `/api/runtime/status`, `/api/outbox/summary`, `/api/integrations/summary`, and workforce/productivity/capacity summaries.

## Service identity contract

Protected requests require all headers:

```http
Authorization: Bearer <service-token>
X-Archive-Source-System: Archive-Market
X-Archive-Service-Scope: production:ingest
```

Allowed identities are deliberately narrow:

| Source | Scope | Allowed operation |
| --- | --- | --- |
| `Archive-Market` | `production:ingest` | Market event ingest |
| `Archive-Logistics` | `logistics:ingest` | daily settlement ingest |
| `ArchiveOS` | `admin:operate` | explicit operational writes |
| `ArchiveOS-Reader` | `authenticated:read` | sensitive synthetic detail reads |
| `Archive-Nexus-Operator` | `admin:operate` | local operator automation only |

The request source header must equal the `source` field in Market and Logistics inbound bodies. Tokens are compared without logging and are never persisted. Protected writes are limited per service identity in a bounded one-minute in-memory window (default: 60); oversized payloads and batches are rejected before processing.

## Compose and secrets

RC Compose has no default database or Grafana credentials. It requires `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`, and the five `ARCHIVE_SECURITY_*_TOKEN` variables. Missing values intentionally fail startup.

Use `docker-compose.dev-db.yml` only for local DBeaver access. It binds PostgreSQL to `127.0.0.1`, never to all interfaces.

## Rotation runbook

1. Generate a separate high-entropy value per service identity outside Git.
2. Inject a new token into the caller and Archive-Nexus secret stores.
3. If a grace period is required, deploy support for the next token explicitly; do not reuse unrelated service tokens.
4. Recreate affected containers and verify a valid request, then a retired-token `401`.
5. Search application and proxy logs for token text; revoke immediately if exposure is found.
6. Remove the retired secret from the runtime secret store. Do not place it in `.env.example`, Compose, README, or screenshots.

## Retry behavior

Authentication failures (`401` / `403`) are configuration failures, not transient delivery failures. They must not be retried indefinitely by the outbox; correct service identity or scope before replaying an event through an approved operational path.

## Remaining production controls

This is an RC baseline. Production still needs a dedicated secrets manager, TLS termination, network policies, centralized rate limiting, an authenticated metrics collector, audit retention, and preferably mTLS or workload identity.
