# Archive-Nexus 최종 제출 요약본

## 1. 핵심 요약

Archive-Nexus는 제조 이벤트를 `eventType` 기반으로 라우팅하는 Outbox 백엔드로,  
물류 이벤트는 Archive-Logistics, 비용/정산성 이벤트는 Archive-Ledger로 분기한다.

## 2. 라우팅 정책

- **Logistics 대상**
  - `LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`,
    `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT`
- **Ledger 대상**
  - `PRODUCTION_COMPLETED`, `MATERIAL_CONSUMED`, `MAINTENANCE_COMPLETED`,
    `QUALITY_DEFECT_DETECTED`, `EMERGENCY_PURCHASE_REQUESTED`,
    `QUALITY_CLAIM_CHARGED`, `CORPORATE_CARD_USED`, `VENDOR_PAYMENT_REQUESTED`
- **NONE**
  - `SHIPMENT_HOLD_CREATED`는 비용 확정 전 단계라 외부 publish 제외

외부 표기는 `Archive-Logistics`로 통일했다. 내부 호환성을 위해 API 파라미터와 환경변수의
`logitics`/`LOGITICS` 값은 유지한다.

## 3. 장애 격리

- 연동 서비스가 꺼져 있어도/비활성이어도 제조 API는 정상 유지
- disabled 상태에서 `publish`는 dry-run 혹은 skipped 처리
- 상태, retry, 오류 정보는 Outbox 메타에 유지

## 4. 최종 smoke 결과

- `GET /api/outbox/summary`: `total 764`, `pending 708`, `published 6`, `pendingRetry 44`, `failed 0`
- `GET /api/integrations/summary`: `status=HEALTHY`, `routing= AUTO`, integrations disabled
- `generate` + `publish?target=auto&dryRun=true`: 후보는 50건 처리, published 0, skipped 50
- `publish?target=logitics` / `publish?target=ledger` dry-run 각각 50건 skip 확인
- 조회 API: target/status 필터 정상 동작

## 5. 검증/빌드

- `./gradlew.bat test --no-daemon --console=plain` ✅
- `./gradlew.bat bootJar --no-daemon --console=plain` ✅
- `docker compose config --quiet` ✅

## 6. 산출물

- `docs/final-smoke-result.md`
- `docs/final-submit-summary.md`
- `docs/final-submit-detailed.md`
- `dist/final-submit-summary.pdf`
- `dist/final-submit-detailed.pdf`

## 7. 참고 문구(포트폴리오)

“Archive-Nexus는 제조·출하 이벤트를 `eventType` 기반 라우팅으로 분기해 물류는 Archive-Logistics, 비용·정산성 이벤트는 Archive-Ledger로 전달합니다.  
외부 장애가 제조 API로 전달되지 않도록 target별 retry, last_error, dry-run, routing summary를 제공합니다.”
