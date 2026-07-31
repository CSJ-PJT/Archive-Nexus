<p align="center">
  <img src="docs/brand/archive-nexus-lockup.svg" width="900" alt="Archive Nexus" />
</p>

# Archive-Nexus

## Archive-Nexus 소개

Archive-Nexus는 제조 운영 환경의 주문/생산/물류/정산 이벤트를 **중앙 허브**로 수집·조정하는 제조 AX 백엔드 플랫폼입니다.
Archive-Market에서 유입되는 주문 이벤트를 기준으로 생산 가용성, 인력 배치, 출고/정산 연동을 조합해 다음 시스템으로 전달합니다.

주요 역할은 다음과 같습니다.

- **이벤트 라우팅 허브**: 다양한 출처의 이벤트를 표준 메시지로 정규화해 `Archive-Logistics`, `Archive-Ledger`, `ArchiveOS`로 전달
- **생산·운영 상태 조정**: workforce/가용 인력·용량 기반으로 생산/품질 운영 파이프라인을 조절
- **Outbox 기반 신뢰성 확보**: 이벤트 전파 신뢰성(재시도/유실 방지)을 위한 메시지 큐 관리
- **운영 모니터링 기반 제공**: 운영 지표, 이벤트 상태, 런타임 히스토리로 시스템의 현재 상태를 가시화

## ?댁쁺 ??븷

- Factory A/B/C ?쒖“ ?고????곹깭 ?앹꽦 諛?議고쉶
- Archive-Market 二쇰Ц쨌?앹궛쨌異쒗븯쨌痍⑥냼쨌諛섑뭹쨌?대젅???대깽???섏떊
- ?⑹꽦 ?댁쁺 ?몃젰 諛곗젙 湲곕컲 ?앹궛 ?λ젰, 誘몄쿂由?臾쇰웾, ?앹궛??怨꾩궛
- ?쒖“/?덉쭏/?뺣퉬/異쒗븯 ?대깽?몃? Outbox?????
- Logistics ?대깽?몃뒗 Archive-Logistics濡??꾨떖
- 鍮꾩슜쨌?뺤궛???대깽?몃뒗 Archive-Ledger濡?吏곸젒 ?꾨떖
- 鍮꾩슜 ?뺤젙 ???대깽?몃뒗 `NONE/SKIPPED` 泥섎━
- ArchiveOS媛 ?쎌쓣 ???덈뒗 ?곕룞, Outbox, ?댁쁺 ?몃젰, 臾쇰쪟 ?뺤궛 ?붿빟 ?쒓났

## ?꾩껜 ?먮쫫

```mermaid
flowchart LR
  Market[Archive-Market<br/>?⑹꽦 而ㅻ㉧???대깽??
  Nexus[Archive-Nexus<br/>Manufacturing AX]
  Workforce[?댁쁺 ?몃젰<br/>泥섎━ ?λ젰 / 誘몄쿂由?臾쇰웾 / ?앹궛??
  Outbox[Nexus Outbox<br/>?쇱슦???뺤콉 / ?ъ떆??/ Dry-run]
  Logistics[Archive-Logistics<br/>寃쎈줈 / ETA / 臾쇰쪟鍮?
  Ledger[Archive-Ledger<br/>嫄곕옒 / ?먯옣 / ?뺤궛]
  OS[ArchiveOS<br/>愿?????

  Market -->|二쇰Ц / ?앹궛 / 異쒗븯 ?붿껌| Nexus
  Nexus --> Workforce
  Workforce -->|?쒖“ ?대깽?? Outbox
  Outbox -->|臾쇰쪟 ??? Logistics
  Outbox -->|?먯옣 吏곸젒 ??? Ledger
  Outbox -->|NONE / SKIPPED| Nexus
  Logistics -->|?쇱씪 ?뺤궛 肄쒕갚| Nexus
  Logistics -->|?뺤젙 臾쇰쪟鍮? Ledger
  Nexus -->|?붿빟 API| OS
  Logistics -->|?붿빟 API| OS
  Ledger -->|?붿빟 API| OS
```

## ?듭떖 湲곕뒫

### 1. ?쒖“ ?고???

- Factory A/B/C ?앹궛, ?덉쭏, ?뺣퉬, ?ш퀬, 臾쇰쪟 ?고????곹깭 ?좎?
- ?쒕??덉씠??start/stop 諛???쒕낫??API ?쒓났
- PostgreSQL/JPA 湲곕컲 ?쒖“ ?곗씠???곸냽??
- Prometheus/Grafana 愿痢?援ъ“ ?좎?

### 2. Archive-Market ?섏떊

Nexus??Archive-Market???⑹꽦 而ㅻ㉧???대깽?몃? ?섏떊?섍퀬, ?꾩슂??寃쎌슦 ?쒖“ Outbox ?대깽?몃줈 蹂?섑빀?덈떎.

| Market ?대깽??| Nexus 泥섎━ |
| --- | --- |
| `MARKET_ORDER_PLACED` | 二쇰Ц 湲곕컲 ?쒖“ ?섏슂濡????|
| `PRODUCTION_REQUESTED` | ?댁쁺 ?몃젰 泥섎━ ?λ젰???뚮え??`PRODUCTION_COMPLETED` ?먮뒗 `BACKLOG_INCREASED` ?앹꽦 |
| `SHIPMENT_REQUESTED` | `LOGISTICS_DISPATCHED` ?먮뒗 `SHIPMENT_HOLD_CREATED` ?앹꽦 |
| `ORDER_CANCELLED` | 異쒗븯 蹂대쪟/痍⑥냼 ?깃꺽???대? ?대깽???앹꽦 |
| `RETURN_REQUESTED` | ?덉쭏/諛섑뭹 愿???대깽?몃줈 ?곌껐 |
| `QUALITY_CLAIM_CREATED` | `QUALITY_CLAIM_CHARGED` ?먮뒗 ?덉쭏 ?대깽?몃줈 ?곌껐 |

Market-origin payload??`orderId`, `customerType`, `riskLevel`, `productType`, `quantity`, `orderAmount`, `priority`, `simulationRunId`, `settlementCycleId`, `correlationId`, `causationId`???섏쐞 ?쒕퉬??異붿쟻???꾪빐 Outbox payload??蹂댁〈?⑸땲??

### 3. ?댁쁺 ?몃젰

ArchiveOS ?먮뒗 Archive-Market???⑹꽦 ?댁쁺 ?몃젰??諛곗젙?섎㈃ Nexus???대떦 泥섎━ ?λ젰??湲곗??쇰줈 ?앹궛 媛?λ웾怨?誘몄쿂由?臾쇰웾??怨꾩궛?⑸땲??

| ??븷 | 湲곕낯 ?섎? |
| --- | --- |
| `PRODUCTION_OPERATOR` | ?앹궛 泥섎━ ?λ젰 |
| `QUALITY_INSPECTOR` | ?덉쭏 寃???λ젰 諛??덉쭏 由ъ뒪??|
| `MAINTENANCE_ENGINEER` | ?뺣퉬 泥섎━ ?λ젰 諛?媛??以묐떒 由ъ뒪??|
| `MATERIAL_HANDLER` | ?먯옱 泥섎━ ?λ젰 |
| `FACTORY_MANAGER` | ?댁쁺 愿由??λ젰 |

`archive.workforce.enabled=false`?대㈃ 湲곗〈 湲곗? 泥섎━ ?λ젰?쇰줈 ?숈옉?⑸땲?? ?쒖꽦 ?곹깭?먯꽌??active allocation??`effectiveCapacity`, `usedCapacity`, `remainingCapacity`瑜??ㅼ젣濡??뚮え?⑸땲??

ArchiveOS Live Flow???몄텧?섎뒗 workforce backlog? bottleneck? ?꾩껜 ?꾩쟻 ?쒖“ ?대젰 ?섍? ?꾨땲???꾩옱 ?댁쁺 ?덈룄??湲곗? projection?낅땲?? ?꾩쟻 synthetic runtime data??蹂댁〈?섎릺, `/api/workforce/summary`, `/api/capacity/summary`, `/api/operations/summary`??愿??媛믪? role蹂?capacity? summary demand window瑜?湲곗??쇰줈 ?곗젙?⑸땲??

### 4. Autonomous Runtime Work Loop

ArchiveOS ?ㅼ떆媛?愿?쒖뿉???쒕퉬?ㅺ? ?뺤???寃껋쿂??蹂댁씠吏 ?딅룄濡?Nexus??local/demo ?섍꼍?먯꽌 ?쒗븳???띾룄??synthetic runtime work loop瑜??ㅽ뻾?????덉뒿?덈떎.

- 湲곕낯 API: `GET /api/runtime/status`
- Compose 湲곕낯媛? `ARCHIVE_RUNTIME_AUTORUN_ENABLED=true`
- 湲곕낯 tick: `ARCHIVE_RUNTIME_TICK_INTERVAL=30s`
- tick??理쒕? ?대깽?? `ARCHIVE_RUNTIME_MAX_EVENTS_PER_TICK=10`
- tick??理쒕? backlog ?섏슂: `ARCHIVE_RUNTIME_MAX_BACKLOG_PER_TICK=50`

猷⑦봽??`MARKET_ORDER_PLACED`, `PRODUCTION_REQUESTED`, `SHIPMENT_REQUESTED`瑜?湲곗〈 Market inbound ?쒕퉬?ㅻ줈 ?섏떊?쒖폒 Workforce capacity? Outbox routing???ㅼ젣濡??듦낵?쒗궢?덈떎. Summary API??read-only?대ŉ ?곗씠?곕? ?앹꽦?섏? ?딆뒿?덈떎.

### 5. Outbox ?쇱슦??

| ???| ?대깽?????| ?ㅻ챸 |
| --- | --- | --- |
| `LOGITICS` | `LOGISTICS_DISPATCHED`, `URGENT_DELIVERY_REQUESTED`, `SHIPMENT_HOLD_RELEASED`, `MATERIAL_TRANSFER_REQUESTED`, `QUALITY_REPLACEMENT_SHIPMENT` | Archive-Logistics媛 route, ETA, ?댁넚鍮? 吏???고쉶 鍮꾩슜 怨꾩궛 |
| `LEDGER` | `PRODUCTION_COMPLETED`, `MATERIAL_CONSUMED`, `MAINTENANCE_COMPLETED`, `QUALITY_DEFECT_DETECTED`, `EMERGENCY_PURCHASE_REQUESTED`, `QUALITY_CLAIM_CHARGED`, `CORPORATE_CARD_USED`, `VENDOR_PAYMENT_REQUESTED` | Archive-Ledger媛 嫄곕옒쨌?먯옣쨌?뺤궛쨌???泥섎━ |
| `NONE` | `SHIPMENT_HOLD_CREATED`, `PRODUCTION_DELAYED`, `BACKLOG_INCREASED`, `MAINTENANCE_REQUIRED` | 鍮꾩슜 ?뺤젙 ???먮뒗 ?대? ?댁쁺 ?곹깭. ?몃? 諛쒗뻾 ?앸왂 |
| `UNKNOWN` | 吏?먰븯吏 ?딅뒗 eventType | 諛쒗뻾?섏? ?딄퀬 ?쇱슦???앸왂/?ㅽ뙣 洹쇨굅 湲곕줉 |

?몃? ?쒓린??`Archive-Logistics`濡??듭씪?⑸땲?? ?대? ?명솚 媛믪씤 `LOGITICS`, `logitics`, `ARCHIVE_INTEGRATIONS_LOGITICS_*`??湲곗〈 DB/API/env 怨꾩빟 ?좎?瑜??꾪빐 洹몃?濡??〓땲??

## 二쇱슂 API

### Outbox / ?곕룞

| 硫붿꽌??| 寃쎈줈 | ?ㅻ챸 |
| --- | --- | --- |
| `GET` | `/api/operations/summary` | ArchiveOS Operational Twin??read-only ?댁쁺 ?붿빟 |
| `GET` | `/api/runtime-events/recent?limit=100` | 理쒖떊 runtime event 議고쉶 |
| `GET` | `/api/runtime-events/correlation/{correlationId}` | correlationId 湲곗? runtime event 異붿쟻 |
| `GET` | `/api/runtime-events/entity/{entityId}` | entityId 湲곗? runtime event 異붿쟻 |
| `GET` | `/api/runtime/status` | Autonomous runtime work loop ?곹깭 |
| `GET` | `/api/outbox/summary` | Outbox ?곹깭/??곷퀎 吏묎퀎 |
| `GET` | `/api/outbox/events` | Outbox ?대깽??紐⑸줉. `status`, `targetService` ?꾪꽣 吏??|
| `GET` | `/api/outbox/events/{eventId}` | ?⑥씪 Outbox ?대깽??議고쉶 |
| `POST` | `/api/outbox/events/generate?count=100&type=logistics` | ?⑹꽦 ?대깽???앹꽦 |
| `POST` | `/api/outbox/events/publish?target=auto&dryRun=true` | dry-run ?쇱슦??寃곌낵 ?뺤씤 |
| `POST` | `/api/outbox/events/publish?target=logitics` | Logistics ????대깽??諛쒗뻾 |
| `POST` | `/api/outbox/events/publish?target=ledger` | Ledger ????대깽??諛쒗뻾 |
| `GET` | `/api/integrations/summary` | Archive-Logistics, Archive-Ledger, ArchiveOS, ?댁쁺 ?몃젰 ?붿빟 |

### Archive-Market ?섏떊

| 硫붿꽌??| 寃쎈줈 | ?ㅻ챸 |
| --- | --- | --- |
| `POST` | `/api/events/market` | Archive-Market ?④굔 ?대깽???섏떊 |
| `POST` | `/api/events/market/bulk` | Archive-Market ?대깽??batch ?섏떊 |
| `GET` | `/api/events/market` | Market ?섏떊 ?대깽??紐⑸줉. `status`, `limit` ?꾪꽣 吏??|

### ?댁쁺 ?몃젰 / ?앹궛??/ 泥섎━ ?λ젰

| 硫붿꽌??| 寃쎈줈 | ?ㅻ챸 |
| --- | --- | --- |
| `POST` | `/api/workforce/allocations` | ?⑹꽦 ?댁쁺 ?몃젰 諛곗젙 ?섏떊 |
| `GET` | `/api/workforce/summary` | ?몄썝, 泥섎━ ?λ젰, 誘몄쿂由?臾쇰웾, ?멸굔鍮??붿빟 |
| `GET` | `/api/productivity/summary` | 理쒖떊 workday ?앹궛??寃곌낵 |
| `GET` | `/api/capacity/summary` | 湲곗?媛??먮뒗 諛곗젙 湲곕컲 泥섎━ ?λ젰 ?붿빟 |
| `POST` | `/api/workforce/workday/run?date=YYYY-MM-DD` | ?⑹꽦 workday snapshot ?앹꽦 |

### 臾쇰쪟 ?뺤궛 肄쒕갚

| 硫붿꽌??| 寃쎈줈 | ?ㅻ챸 |
| --- | --- | --- |
| `POST` | `/api/logistics/settlements/daily` | Archive-Logistics???⑹꽦 ?쇱씪 ?쒖“ ?뺤궛 ?섏떊 |
| `POST` | `/api/logistics/settlements/daily/bulk` | ?뺤궛 肄쒕갚 batch ?섏떊 |
| `GET` | `/api/logistics/settlements` | ?섏떊 ?뺤궛 紐⑸줉 |
| `GET` | `/api/logistics/settlements/summary` | ?뺤궛 肄쒕갚 ?붿빟 |

### Platform / ArchiveOS

| 硫붿꽌??| 寃쎈줈 | ?ㅻ챸 |
| --- | --- | --- |
| `GET` | `/api/archiveos/status` | ArchiveOS 媛???곹깭 |
| `GET` | `/api/platform/manifest` | Archive Suite ??怨꾩빟 |
| `GET` | `/actuator/health` | Spring Boot health |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

## ?섍꼍 蹂??

湲곕낯媛믪? 濡쒖뺄/demo ?섍꼍 湲곗??낅땲?? ?ㅼ젣 secret, token, webhook, private key??而ㅻ컠?섏? ?딆뒿?덈떎.

```powershell
# ArchiveOS
ARCHIVEOS_BASE_URL=http://host.docker.internal:4000

# Archive-Logistics ?곕룞
ARCHIVE_INTEGRATIONS_LOGITICS_ENABLED=true
ARCHIVE_INTEGRATIONS_LOGITICS_BASE_URL=http://host.docker.internal:8092
ARCHIVE_INTEGRATIONS_LOGITICS_BULK_ENDPOINT=/api/events/nexus/bulk
ARCHIVE_INTEGRATIONS_LOGITICS_TIMEOUT_MS=30000

# Archive-Ledger ?곕룞
ARCHIVE_INTEGRATIONS_LEDGER_ENABLED=true
ARCHIVE_INTEGRATIONS_LEDGER_BASE_URL=http://host.docker.internal:18080
ARCHIVE_INTEGRATIONS_LEDGER_BULK_ENDPOINT=/api/events/nexus/bulk
ARCHIVE_INTEGRATIONS_LEDGER_TIMEOUT_MS=30000

# 湲곗〈 Ledger ?명솚 ??
ARCHIVE_LEDGER_ENABLED=true
ARCHIVE_LEDGER_BASE_URL=http://host.docker.internal:18080
ARCHIVE_LEDGER_TIMEOUT_MS=30000

# ?쇱슦??
ARCHIVE_INTEGRATIONS_ROUTING_MODE=AUTO
ARCHIVE_INTEGRATIONS_ROUTING_CHUNK_SIZE=50
ARCHIVE_INTEGRATIONS_ROUTING_MAX_RETRY_COUNT=5
ARCHIVE_INTEGRATIONS_ROUTING_ALLOW_LEDGER_DIRECT_FALLBACK_FOR_LOGISTICS=false

# Archive-Market / ?댁쁺 ?몃젰
ARCHIVE_INTEGRATIONS_MARKET_ENABLED=false
ARCHIVE_WORKFORCE_ENABLED=false
ARCHIVE_WORKFORCE_BASELINE_CAPACITY=120
```

## 濡쒖뺄 ?ㅽ뻾

```powershell
docker compose up --build -d
```

二쇱슂 URL:

| ?쒕퉬??| URL |
| --- | --- |
| Nexus ?꾨줎?몄뿏??| `http://localhost:15173` |
| Nexus 諛깆뿏??| `http://localhost:8080` |
| Prometheus | `http://localhost:19090` |
| Grafana | `http://localhost:13000` |

Archive-Logistics, Archive-Ledger, Archive-Market, ArchiveOS??媛???μ냼?먯꽌 蹂꾨룄濡??ㅽ뻾?⑸땲?? Nexus docker-compose?먮뒗 ?몃? ?쒕퉬?ㅻ? 吏곸젒 ?ы븿?섏? ?딄퀬 URL 湲곕컲?쇰줈 ?곕룞?⑸땲??

## ?ㅻえ???뚯뒪??

PowerShell 湲곗? ?덉떆?낅땲??

```powershell
curl.exe "http://localhost:8080/api/integrations/summary"
curl.exe "http://localhost:8080/api/outbox/summary"
curl.exe "http://localhost:8080/api/workforce/summary"

curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=logistics"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=auto&dryRun=true"

curl.exe -X POST "http://localhost:8080/api/outbox/events/generate?count=20&type=ledger"
curl.exe -X POST "http://localhost:8080/api/outbox/events/publish?target=auto&dryRun=true"
```

Market ?섏떊 ?ㅻえ??

```powershell
curl.exe -X POST "http://localhost:8080/api/events/market" `
  -H "Content-Type: application/json" `
  -d "{\"eventId\":\"MK-PROD-001\",\"idempotencyKey\":\"MK-PROD-001\",\"source\":\"Archive-Market\",\"eventType\":\"PRODUCTION_REQUESTED\",\"schemaVersion\":1,\"occurredAt\":\"2026-07-10T00:00:00Z\",\"simulationRunId\":\"SIM-001\",\"settlementCycleId\":\"CYCLE-001\",\"correlationId\":\"CORR-001\",\"causationId\":\"CAUSE-001\",\"hopCount\":0,\"maxHop\":8,\"payload\":{\"orderId\":\"ORD-001\",\"customerId\":\"SYN-CUSTOMER-001\",\"customerType\":\"B2B\",\"riskLevel\":\"LOW\",\"productType\":\"BATTERY_PACK\",\"quantity\":10,\"orderAmount\":1200000,\"priority\":\"NORMAL\",\"requiresShipment\":true}}"
```

?댁쁺 ?몃젰 諛곗젙 ?ㅻえ??

```powershell
curl.exe -X POST "http://localhost:8080/api/workforce/allocations" `
  -H "Content-Type: application/json" `
  -d "{\"eventId\":\"WF-001\",\"idempotencyKey\":\"WF-001\",\"sourceService\":\"ArchiveOS\",\"targetService\":\"Archive-Nexus\",\"eventType\":\"WORKFORCE_ALLOCATION_ASSIGNED\",\"role\":\"PRODUCTION_OPERATOR\",\"allocatedHeadcount\":5,\"capacityPerPersonPerDay\":20,\"productivityScore\":0.9,\"wagePerDay\":120000,\"workdayId\":\"WD-20260710\",\"simulationRunId\":\"SIM-001\",\"settlementCycleId\":\"CYCLE-001\",\"correlationId\":\"CORR-WF-001\",\"causationId\":\"CAUSE-WF-001\",\"hopCount\":0,\"maxHop\":8,\"reason\":\"Synthetic production capacity allocation\"}"

curl.exe "http://localhost:8080/api/workforce/summary"
```

## 寃利?紐낅졊

```powershell
cd backend
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat bootJar --no-daemon --console=plain
cd ..
docker compose config --quiet
```

## ?댁쁺 ?먯튃

- `dryRun=true`???몃? HTTP ?몄텧 ?놁씠 ?쇱슦??寃곌낵留??뺤씤?⑸땲??
- 鍮꾪솢?깊솕???곕룞? ?ㅼ젣 諛쒗뻾?섏? ?딄퀬 `SKIPPED` ?먮뒗 dry-run ?깃꺽??寃곌낵濡?諛섑솚?⑸땲??
- ?섏쐞 ?쒕퉬???μ븷??`retry_count`, `last_error`, `last_publish_target`, `last_publish_attempt_at`???④퉩?덈떎.
- `hopCount > maxHop` ?대깽?몃뒗 ?쒗솚 援ъ“ 諛⑹?瑜??꾪빐 reject/ignored 泥섎━?⑸땲??
- `eventId`? `idempotencyKey`濡?以묐났 泥섎━瑜?諛⑹??⑸땲??
- Market-origin ?대깽?몃뒗 ?ㅼ떆 Archive-Market?쇰줈 諛쒗뻾?섏? ?딆뒿?덈떎.
- Workforce ?대깽?몃뒗 ?ㅼ떆 ?댁쁺 ?몃젰 諛곗젙??臾댄븳 ?앹꽦?섏? ?딆뒿?덈떎.
- Logistics/Ledger/ArchiveOS ?μ븷??Nexus ?쒖“ API ?μ븷濡??꾪뙆?섏? ?딆뒿?덈떎.

## ?ㅺ뎅??UI

?꾨줎?몄뿏?쒕뒗 ?곗륫 ?곷떒 吏援щ낯 硫붾돱?먯꽌 ?ㅼ쓬 ?몄뼱瑜?吏?먰빀?덈떎.

- ?쒓뎅??(`ko`, 湲곕낯媛?
- ?곸뼱 (`en`)
- ?쇰낯??(`ja`)
- 以묎뎅??媛꾩껜 (`zh-CN`)

?좏깮 ?몄뼱??`localStorage`??`archive.locale`????λ맗?덈떎. API path, eventType, enum, ID, repository紐? ArchiveOS/Archive-Nexus/Archive-Logistics/Archive-Ledger 媛숈? 怨좎쑀紐낆궗??踰덉뿭?섏? ?딄퀬 UI label留?踰덉뿭?⑸땲??

## 臾몄꽌

- [?꾪궎?띿쿂](docs/architecture.md)
- [API 李멸퀬 臾몄꽌](docs/api-reference.md)
- [ArchiveOS Live Flow 怨꾩빟](docs/archiveos-live-flow-contract.md)
- [Runtime Event 怨꾩빟](docs/runtime-event-contract.md)
- [Nexus Runtime Event 怨꾩빟](docs/nexus-runtime-event-contract.md)
- [Operations Summary 怨꾩빟](docs/operations-summary-contract.md)
- [Nexus Workforce Capacity 怨꾩빟](docs/nexus-workforce-capacity-contract.md)
- [Outbox ?쇱슦??(docs/outbox-routing.md)
- [Archive-Market ?곕룞 怨꾩빟](docs/market-integration-contract.md)
- [?댁쁺 ?몃젰 紐⑤뜽](docs/operational-workforce.md)
- [Nexus ?댁쁺 ?몃젰 紐⑤뜽](docs/nexus-workforce-model.md)
- [Nexus ?앹궛??紐⑤뜽](docs/nexus-productivity-model.md)
- [?댁쁺 ?몃젰 ?대깽??怨꾩빟](docs/workforce-event-contract.md)
- [Archive-Logistics 怨꾩빟](docs/nexus-logitics-contract.md)
- [Archive-Ledger 怨꾩빟](docs/nexus-ledger-contract.md)
- [臾쇰쪟 ?쇱씪 ?뺤궛 ?섏떊??(docs/logistics-daily-settlement-inbox.md)
- [?ㅻえ???뚯뒪??(docs/smoke-test.md)
- [?댁쁺 ?곕턿](docs/operations-runbook.md)

## 湲곗닠 ?ㅽ깮

- Java 21
- Spring Boot 3
- Spring Web / Validation / Actuator
- Spring Data JPA
- PostgreSQL
- Flyway
- Prometheus / Grafana
- Docker Compose
- React ?꾨줎?몄뿏??

## ?곗씠???덉쟾 湲곗?

- ?ㅼ젣 媛쒖씤?뺣낫, ?ㅼ젣 湲덉쑖?뺣낫, ?ㅼ젣 寃곗젣?뺣낫, ?ㅼ젣 諛곗넚二쇱냼, ?ㅼ젣 吏곸썝/湲됱뿬 ?곗씠?곕? ?ъ슜?섏? ?딆뒿?덈떎.
- 怨좉컼 ID, 怨꾩젙 ID, 移대뱶 token, ?댁쁺 ?몃젰, 湲덉븸, 二쇰Ц, ?뺤궛 媛믪? 紐⑤몢 synthetic/demo ?앸퀎?먯? 湲덉븸?낅땲??
- `.env`, token, webhook, private key, API key, local DB/data/build output? Git??而ㅻ컠?섏? ?딆뒿?덈떎.

## RC 蹂댁븞 湲곗?

Release Candidate Compose??PostgreSQL??Docker private network濡??쒗븳?섍퀬 backend/frontend/monitoring ?ы듃瑜?`127.0.0.1`?먮쭔 諛붿씤?⑺빀?덈떎. ?대? write API???쒕퉬??ID쨌scope쨌Bearer token???붽뎄?섎ŉ, ?ㅼ젣 ?좏겙怨?湲곕낯 鍮꾨?踰덊샇????μ냼???ы븿?섏? ?딆뒿?덈떎. ?곸꽭 怨꾩빟怨?rotation ?덉감??[RC Security Baseline](docs/rc-security-baseline.md)??李멸퀬?섏꽭??

> Runtime Mesh V1: ArchiveOS??`GET /api/runtime/status`, `GET /api/runtime-events/recent?after={cursor}`, `GET /api/operations/summary`???듯빐 Nexus??Synthetic Runtime Data瑜??쎄린 ?꾩슜?쇰줈 ?섏쭛?????덉뒿?덈떎. ?먯꽭??怨꾩빟? [Archive Runtime Mesh V1](docs/archive-runtime-mesh-contract.md)??李멸퀬?섏꽭??

> Workforce Driven Manufacturing Runtime: ?앹궛 ?붿껌? ?댁쁺?먃룹옄??룻뭹吏댟룹젙鍮?泥섎━ ?λ젰???ㅼ젣濡?李④컧?섎ŉ, ?덉쭏 寃?щ? 留덉튇 ?섎웾留?異쒗븯 ?대깽?몃줈 ?곌껐?⑸땲?? ?먯꽭???먮쫫? [?쒖“ ?고???紐⑤뜽](docs/workforce-driven-manufacturing-runtime.md)??李멸퀬?섏꽭??

Archive-Nexus???쒖“쨌異쒗븯 ?대깽?몃? ?앹꽦?섍퀬, Archive-Market 二쇰Ц???앹궛 ?먮쫫?쇰줈 諛쏆븘?ㅼ씠硫? ?⑹꽦 ?댁쁺 ?몃젰??泥섎━ ?λ젰???곕씪 ?앹궛 泥섎━?됀룸?泥섎━ 臾쇰웾쨌?덉쭏/?뺣퉬 由ъ뒪?щ? 怨꾩궛?섎뒗 Manufacturing AX 諛깆뿏?쒖엯?덈떎.

Nexus???앹꽦???쒖“ ?대깽?몃? Outbox????ν븳 ??`eventType` 湲곕컲 ?쇱슦???뺤콉???곕씪 Archive-Logistics ?먮뒗 Archive-Ledger濡??꾨떖?⑸땲?? ?몃? ?쒕퉬?ㅺ? 鍮꾪솢?깊솕?섍굅??以묐떒?섏뼱???쒖“ API, ?쒕??덉씠?? ??쒕낫?쒓? 以묐떒?섏? ?딅룄濡??μ븷瑜?寃⑸━?⑸땲??

> 紐⑤뱺 二쇰Ц, 怨좉컼, 湲덉븸, ?몃젰, ?뺤궛 ?곗씠?곕뒗 Synthetic Data / Demo Data?낅땲?? ?ㅼ젣 怨좉컼 ?뺣낫, ?ㅼ젣 寃곗젣 ?뺣낫, ?ㅼ젣 諛곗넚 二쇱냼, ?ㅼ젣 吏곸썝/湲됱뿬/媛쒖씤?뺣낫瑜???ν븯吏 ?딆뒿?덈떎.


