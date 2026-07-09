# Archive-Nexus 최종 제출 상세본

## 1. 프로젝트 역할

Archive-Nexus는 Factory A/B/C의 제조 이벤트를 Outbox에 저장하고, 이벤트 타입 기반 라우팅으로 외부 도메인 서비스로 분기 발행한다.

- 물류 성격 이벤트: `Archive-Logitics` 경유로 비용 확정 처리
- 정비/구매/품질/카드성 비용 이벤트: `Archive-Ledger`로 직접 전달
- 핵심 원칙: 외부 장애가 제조 API에 전파되지 않도록 제조 API의 정상 동작을 보장

## 2. 라우팅 정책(최종 반영)

### Logitics 대상 이벤트

- `LOGISTICS_DISPATCHED`
- `URGENT_DELIVERY_REQUESTED`
- `SHIPMENT_HOLD_RELEASED`
- `MATERIAL_TRANSFER_REQUESTED`
- `QUALITY_REPLACEMENT_SHIPMENT`

### Ledger 대상 이벤트

- `PRODUCTION_COMPLETED`
- `MATERIAL_CONSUMED`
- `MAINTENANCE_COMPLETED`
- `QUALITY_DEFECT_DETECTED`
- `EMERGENCY_PURCHASE_REQUESTED`
- `QUALITY_CLAIM_CHARGED`
- `CORPORATE_CARD_USED`
- `VENDOR_PAYMENT_REQUESTED`

### 예외 처리

- `SHIPMENT_HOLD_CREATED` → `target=NONE` (비용 확정 이전 이벤트라 외부 publish 미실행)
- 미정의 타입 → `target=UNKNOWN` 처리 후 publish skip

## 3. smoke 실행 절차 및 결과

### 선행

```bash
pwd
git remote -v
git branch --show-current
git status -sb
git log --oneline -n 5
```

### API 확인

1. `GET /api/outbox/summary`
2. `GET /api/integrations/summary`
3. `POST /api/outbox/events/generate?count=20&type=logistics`
4. `POST /api/outbox/events/publish?target=auto&dryRun=true`
5. `POST /api/outbox/events/generate?count=20&type=ledger`
6. `POST /api/outbox/events/publish?target=auto&dryRun=true`
7. `POST /api/outbox/events/publish?target=logitics&dryRun=true`
8. `POST /api/outbox/events/publish?target=ledger&dryRun=true`

### 결과 요약 (동일 실행 컨텍스트 기준)

```text
GET /api/outbox/summary
  total: 764
  pending: 708
  published: 6
  pendingRetry: 44
  failed: 0
  LOGITICS: candidates 349 / pending 343 / published 6
  LEDGER:   candidates 403 / pending 403 / published 0
  NONE:     candidates 12 / pending 6 / published 0

GET /api/integrations/summary
  status: HEALTHY
  routing.mode: AUTO
  allowLedgerDirectFallbackForLogistics: false
  logitics.enabled=false / status=DISABLED
  ledger.enabled=false / status=DISABLED

publish dry-run
  auto:    candidates 50 / published 0 / skipped 50 / failed 0
  logitics:candidates 50 / published 0 / skipped 50 / failed 0
  ledger:  candidates 50 / published 0 / skipped 50 / failed 0
```

## 4. 장애 격리 검증

- disabled 상태에서 `/api/outbox/summary`·`/api/integrations/summary`는 200 응답
- 제조 API의 핵심 기능(요약/조회/생성)은 영향 없음
- target publish 실패/스킵 시 `retry_count`, `last_error`, 라우팅 상태 메타 유지

## 5. 코드 변경 내역

### 파일

- `backend/src/main/java/com/archivenexus/backend/outbox/OutboxEventService.java`
  - `publishPending()` 후보 조회 로직을 `publishTarget`별 분기 조회로 보완
  - AUTO: 기존 후보 필터 유지
  - LOGITICS/LEDGER: targetService 별 조회로 누락 없는 후보 선택
  - LEDGER에 fallback이 꺼진 경우 logistics 이벤트는 자동으로 포함되지 않음

### 변경 이유

- `target=logitics`, `target=ledger` 호출 시 과거 후보 누락 사례를 방지하고 라우팅 일치도 일관성 강화

## 6. 문서/검증 자산

- `docs/final-smoke-result.md`: 최종 smoke 증빙
- `docs/final-submit-summary.md`: 요약본
- `docs/final-submit-detailed.md`: 상세본
- `dist/final-submit-summary.pdf`: PDF 요약
- `dist/final-submit-detailed.pdf`: PDF 상세

## 7. 테스트 및 빌드

```bash
cd backend
./gradlew.bat test --no-daemon --console=plain
./gradlew.bat bootJar --no-daemon --console=plain

cd ..
docker compose config --quiet
```

- test: PASS
- bootJar: PASS
- docker compose config: PASS

## 8. 남은 액션

- Archive-Logitics, Archive-Ledger 실제 실행 환경에서의 publish success/fail 통합 smoke는 별도 환경에서 추가 수행이 권장됨

