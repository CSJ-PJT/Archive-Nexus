# Archive-Nexus 최종 스모크 결과

## 1) 개요

최종 제출 전 점검에서 Archive-Nexus Outbox 라우팅과 장애 격리 정책을 재확인했다.

- 대상 저장소: `CSJ-PJT/Archive-Nexus`
- 브랜치: `main`
- 확인 시점: `2026-07-09`
- 기본 연동 상태: `ARCHIVE_INTEGRATIONS_*_ENABLED=false`

## 2) 라우팅 정책

### Logistics 대상

- `LOGISTICS_DISPATCHED`
- `URGENT_DELIVERY_REQUESTED`
- `SHIPMENT_HOLD_RELEASED`
- `MATERIAL_TRANSFER_REQUESTED`
- `QUALITY_REPLACEMENT_SHIPMENT`

### Ledger 직접 대상

- `PRODUCTION_COMPLETED`
- `MATERIAL_CONSUMED`
- `MAINTENANCE_COMPLETED`
- `QUALITY_DEFECT_DETECTED`
- `EMERGENCY_PURCHASE_REQUESTED`
- `QUALITY_CLAIM_CHARGED`
- `CORPORATE_CARD_USED`
- `VENDOR_PAYMENT_REQUESTED`

### NONE 처리

- `SHIPMENT_HOLD_CREATED`는 비용 확정 이전 단계이므로 `target=NONE` 처리

### UNKNOWN 처리

- 지원하지 않는 `eventType`은 UNKNOWN 처리 후 스킵으로 반영

## 3) 기본 스모크 결과 (`GET /api/outbox/summary`)

```json
{
  "total": 764,
  "pending": 708,
  "published": 6,
  "pendingRetry": 44,
  "failed": 0,
  "targets": {
    "LOGITICS": { "candidates": 349, "pending": 343, "published": 6 },
    "LEDGER":   { "candidates": 403, "pending": 403, "published": 0 },
    "NONE":     { "candidates": 12,  "pending": 6,   "published": 0 },
    "UNKNOWN":  { "candidates": 0,   "pending": 0,   "published": 0 }
  }
}
```

- `GET /api/integrations/summary` 응답
  - `status: HEALTHY`
  - `routing.mode: AUTO`
  - `allowLedgerDirectFallbackForLogistics: false`
  - `logitics.enabled=false`, `ledger.enabled=false`, 상태 `DISABLED`

## 4) 실행 커맨드 및 핵심 결과

### 4-1. 타깃 생성 + dry-run

1. `POST /api/outbox/events/generate?count=10&type=logistics`
   - `requested=10`, `generated=10`, `targets.LOGITICS=10`
2. `POST /api/outbox/events/publish?target=auto&dryRun=true`
   - `totalCandidates=50`, `attempted=50`, `published=0`, `skipped=50`, `failed=0`
3. `POST /api/outbox/events/generate?count=10&type=ledger`
   - `requested=10`, `generated=10`, `targets.LEDGER=10`
4. `POST /api/outbox/events/publish?target=auto&dryRun=true`
   - `totalCandidates=50`, `attempted=50`, `published=0`, `skipped=50`, `failed=0`

### 4-2. 타깃별 dry-run

- `POST /api/outbox/events/publish?target=logitics&dryRun=true`
  - `target=LOGITICS`, `totalCandidates=50`, `published=0`, `skipped=50`, `failed=0`
- `POST /api/outbox/events/publish?target=ledger&dryRun=true`
  - `target=LEDGER`, `totalCandidates=50`, `published=0`, `skipped=50`, `failed=0`

### 4-3. 조회 API 확인

- `GET /api/outbox/events?targetService=LOGITICS&status=PENDING&limit=2000` → 343건
- `GET /api/outbox/events?targetService=LEDGER&status=PENDING&limit=2000` → 359건
- `GET /api/outbox/events?targetService=NONE&status=PENDING&limit=2000` → 6건
- `GET /api/outbox/events?status=PENDING_RETRY&limit=2000` → 44건

## 5) 장애 격리 검증

- `enabled=false` 상태에서도 제조 API는 정상 동작
- 외부 호출이 필요 없는 경로에서 200 응답 유지
- 기존 제조 화면/이벤트 조회 API는 정상 동작 유지

## 6) 코드 보완

- `publish` 대상 조회 경로 누락 이슈를 보완
  - `target=logitics`, `target=ledger`에서 대상별 쿼리로 후보 선별
  - AUTO는 기존 동작 유지
- `routingStatus`, `last_error`, `last_publish_attempt_at` 기록 동작 유지

## 7) 잔여 검증 항목

- `Archive-Logistics`, `Archive-Ledger`를 실제 동작 상태로 올린 연동 smoke(실서비스 경유 publish) 검증은 별도 환경에서 추가 실행 필요

