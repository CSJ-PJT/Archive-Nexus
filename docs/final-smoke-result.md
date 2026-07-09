# Archive-Nexus Final Smoke Result

## 개요

목표는 Event Outbox 라우팅 정책이 제조/출하 이벤트를 eventType 기준으로 `Archive-Logitics`와 `Archive-Ledger`로 정확히 구분하는지 확인하고, 장애 격리 동작까지 검증하는 것입니다.

## Nexus 역할 (최종 문구 정리)

- Archive-Nexus는 제조·출하 이벤트를 생성하고 Outbox 라우팅 정책에 따라 물류 이벤트는 `Archive-Logitics`, 정비·구매·품질·카드성 비용 이벤트는 `Archive-Ledger`로 전달합니다.
- 외부 서비스 장애가 제조 API로 전파되지 않도록 `target` 단위의 `retry`, `last_error`, `dry-run`, `routing summary`를 제공합니다.
- 현재 통합 설정은 두 외부 연동이 기본적으로 `disabled` 상태입니다.

## 라우팅 정책

- Logistics 대상
  - `LOGISTICS_DISPATCHED`
  - `URGENT_DELIVERY_REQUESTED`
  - `SHIPMENT_HOLD_RELEASED`
  - `MATERIAL_TRANSFER_REQUESTED`
  - `QUALITY_REPLACEMENT_SHIPMENT`
- Ledger 직접 대상
  - `PRODUCTION_COMPLETED`
  - `MATERIAL_CONSUMED`
  - `MAINTENANCE_COMPLETED`
  - `QUALITY_DEFECT_DETECTED`
  - `EMERGENCY_PURCHASE_REQUESTED`
  - `QUALITY_CLAIM_CHARGED`
  - `CORPORATE_CARD_USED`
  - `VENDOR_PAYMENT_REQUESTED`
- NONE/SKIP
  - `SHIPMENT_HOLD_CREATED`
- UNKNOWN
  - 미지원 `eventType`

## smoke 실행 결과

- `GET /api/outbox/summary`
  - `total: 480`
  - `pending: 475`, `published: 0`, `pendingRetry: 0`, `failed: 0`, `skipped: 5`
  - `targets`: `LOGITICS` 207, `LEDGER` 261, `NONE` 12, `UNKNOWN` 0
  - `integrations`: `ledger` disabled, `logitics` disabled
- `GET /api/integrations/summary`
  - `status: HEALTHY`
  - `integrations`: 두 서비스 모두 `DISABLED`
  - `routing.mode: AUTO`, `allowLedgerDirectFallbackForLogistics: false`

### dry-run publish

- `POST /api/outbox/events/generate?count=20&type=logistics`
  - 생성 결과: `targets.LOGITICS=20`
- `POST /api/outbox/events/publish?target=auto&dryRun=true`
  - `totalCandidates: 50`, `attempted: 50`
  - `published: 0`, `skipped: 50`, `failed: 0`
  - `targets.LOGITICS`와 `targets.LEDGER` 후보 집계가 반환됨
- `POST /api/outbox/events/generate?count=20&type=ledger`
  - 생성 결과: `targets.LEDGER=20`
- `POST /api/outbox/events/publish?target=auto&dryRun=true`
  - `totalCandidates: 50`, `attempted: 50`
  - `published: 0`, `skipped: 50`, `failed: 0`

## disabled 상태 동작

- `ARCHIVE_INTEGRATIONS_*_ENABLED=false` 상태에서 제조 API는 정상 동작.
- `/api/outbox/summary`와 `/api/integrations/summary`는 200 응답.
- `dryRun=true`에서 외부 HTTP 호출 없이 라우팅 후보 통계만 반환.
- publish 대상이 `DISABLED`인 경우 `PENDING_RETRY`/`FAILED`로 전환되지 않음(현재 응답 기준).

## ArchiveOS가 읽는 API 목록

- `GET /api/outbox/summary`
- `GET /api/integrations/summary`
- `GET /api/outbox/events?targetService=LOGITICS`
- `GET /api/outbox/events?targetService=LEDGER`
- `GET /api/outbox/events?status=FAILED`
- `GET /api/outbox/events?status=PENDING_RETRY`

## 남은 이슈

- 현재는 dry-run 기반 검증 중심이며, Archive-Logitics/Archive-Ledger 구동 환경에서 실제 publish 통합 smoke는 별도 환경에서 추가 실행 필요.
- 라우팅 정책 자체는 API/요약 응답에서 일관되게 노출되며, 기본 스모크 기준에는 적합.
