# Portfolio Bullets

## Archive-Nexus · Event Routing for Manufacturing AX

Archive-Nexus의 제조/출하 이벤트 Outbox를 eventType 기반으로 라우팅하도록 확장했습니다.
물류 이벤트는 Archive-Logitics로 전달해 경로·운송비 계산 후 Archive-Ledger로 이어지게 하고,
정비·구매·품질·카드성 비용 이벤트는 Archive-Ledger로 직접 발행하도록 분리했습니다.
외부 서비스 장애가 제조 API로 전파되지 않도록 target별 retry, last_error, dry-run, routing summary를 제공했습니다.

## Resume version

- Implemented eventType-based routing for the Archive-Nexus synthetic domain event outbox, separating logistics operations from direct financial settlement events.
- Routed `LOGISTICS_DISPATCHED` and related shipment events to Archive-Logitics first, while sending maintenance, purchase, quality, corporate-card-like, and vendor-payment events directly to Archive-Ledger.
- Added target-level retry state, last error tracking, dry-run publishing, disabled-integration isolation, and ArchiveOS-readable routing/integration summaries.
