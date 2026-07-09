# Portfolio Bullets

## Archive-Nexus · Event Routing for Manufacturing AX

Archive-Nexus는 제조·출하 이벤트를 생성하고 Outbox 라우팅 정책에 따라 물류 이벤트는 `Archive-Logistics`로, 정비·구매·품질·카드성 비용 이벤트는 `Archive-Ledger`로 전달하는 Manufacturing AX 백엔드입니다.  
외부 서비스 장애가 제조 API로 전파되지 않도록 `target` 단위의 retry, dry-run, routing summary, last_error를 제공합니다.

## Resume version

- Manufacturing outbox를 eventType 기반으로 라우팅해 물류 이벤트와 금융성 이벤트를 분리 전달하도록 구현했습니다.
- 물류 이벤트(`LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`, `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT`)는 `Archive-Logistics`를 경유해 처리하도록 설계했습니다.
- 정비/구매/품질/카드성 비용 이벤트는 `Archive-Ledger`로 직접 전달하여 정산·승인·원장 처리의 책임을 분리했습니다.
- 장애 격리를 위해 통합 비활성/비가용 상태에서도 제조 API/시뮬레이터는 유지되며, 실패 이벤트는 `PENDING_RETRY`/`FAILED`와 `last_error`로 추적합니다.
