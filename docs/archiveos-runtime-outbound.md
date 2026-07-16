# ArchiveOS Runtime Outbound

Archive-Nexus는 제조 runtime 관제 이벤트를 기존 Logistics/Ledger business outbox와 분리된 `archiveos_runtime_delivery`에 저장합니다. 모든 값은 Synthetic Runtime Data이며 delivery 실패는 제조 트랜잭션을 롤백하지 않습니다.

## 발행 계약

`POST {ARCHIVEOS_BASE_URL}/api/live-flow/events/ingest`에 `archive-nexus` / `runtime:ingest` service identity를 사용합니다. `eventId`는 delivery의 멱등 키이며, `correlationId`, `orderId`, `causationId`는 원본 값이 있을 때만 전달합니다. placeholder는 생성하지 않습니다.

## 상태와 재시도

`PENDING → PUBLISHING → PUBLISHED`가 정상 흐름입니다. timeout, 408, 429, 5xx는 backoff 후 `RETRY_WAIT`, 401/403은 `CONFIG_ERROR`, 다른 4xx와 명시적 거절은 `NON_RETRYABLE_ERROR`입니다. 상한 초과 시 `FAILED`로 남습니다.

자동 발행은 `ARCHIVEOS_RUNTIME_INGEST_ENABLED=true`일 때만 실행하며 bounded batch와 scheduler lock을 사용합니다. 조회 API는 publish나 retry를 실행하지 않습니다.

## 운영 조회

- `GET /api/runtime-outbound/summary`
- `GET /api/runtime-outbound/events?status=&correlationId=&limit=`
- `GET /api/runtime-outbound/correlation/{correlationId}/preview`

이 API들은 authenticated read scope가 필요합니다.
