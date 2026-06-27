# 운영 대시보드

Archive Nexus 대시보드는 여러 가상 공장의 생산, 재고, 품질, 정비, 물류, RPA 상태를 한 화면에서 확인하는 운영 관제 UI다. 소개용 랜딩 페이지가 아니라 반복 조회와 승인 업무를 위한 작업 화면으로 구성한다.

`GET /api/dashboard/summary`는 simulator/persistence 상태, domain별 운영 수치, anomaly와 Manufacturing AI 운영 요약을 제공하는 서버 측 Dashboard 계약이다. 기존 개별 domain API는 상세 화면에서 계속 사용한다.

## 데이터 갱신

- 최초 진입과 이후 5초 주기로 모든 운영 API를 병렬 조회한다.
- 우측 상단 새로고침 버튼으로 즉시 동기화할 수 있다.
- 조회 실패 시 마지막 정상 데이터를 유지하고 오류 메시지를 표시한다.
- 최근 동기화 시각은 모든 API 조회가 성공한 시점을 사용한다.
- Start/Stop, RPA 승인/반려 후에는 전체 운영 데이터를 다시 조회한다.
- 브라우저는 동일 출처 `/api`를 호출한다. Vite 개발 서버와 Compose Nginx가 각각 backend reverse proxy를 제공하므로 별도 CORS 설정이 필요하지 않다.

## 화면별 데이터 계약

| 화면 | API | 주요 표시 항목 |
|---|---|---|
| Overview | `/api/overview` 및 전체 도메인 API | 생산 달성률, 평균 불량률, 재고 위험, 출하 지연, 경보, 공장별 상태 |
| Factories | `/api/overview`, 도메인 API | 공장별 오더, 검사, 출하, 정비, 경보 수 |
| Production | `/api/production/orders` | 목표·실적·달성률·상태 |
| Inventory | `/api/inventory/items`, `/api/inventory/transactions` | 현재고·안전재고·재고 위험·최근 입출고 |
| Quality | `/api/quality/inspections` | Lot별 불량률과 판정 |
| Maintenance | `/api/maintenance/events` | 설비·심각도·원인·처리 상태 |
| Logistics | `/api/logistics/shipments` | 출하 목적지·우선순위·지연 상태 |
| RPA | `/api/rpa/tasks` | 작업 상태·추천 조치·승인/반려 |
| Settings | persistence, batch, ArchiveOS API | 저장소 상태·집계 snapshot·연동 이력 |
| Manufacturing AI | `/api/ai/query`, `/api/ai/queries`, `/api/ai/summary` | 자연어 질문, routing, Agent 결과, 근거, 권장 조치, 실행 이력 |

## 상태 판정

- 공장 상태는 최근 경보 중 `CRITICAL` 존재 여부를 최우선으로 판정한다.
- 재고 위험은 `quantity <= safetyStock` 조건으로 계산한다.
- 출하 지연은 `status == DELAYED` 조건으로 계산한다.
- 생산 달성률은 전체 `producedQuantity / targetQuantity`로 계산한다.
- 평균 불량률은 현재 품질 검사 결과의 산술 평균이다.

대시보드 계산은 운영 가시화를 위한 파생 값이며 원본 시뮬레이터 상태를 변경하지 않는다. 서버 측 규칙이나 승인 정책의 판단 근거로 사용하지 않는다.

## 다음 운영 단계

다음 단계에서는 Spring Boot Actuator 기반 서비스 상태, API 지연·오류율, 시뮬레이터 heartbeat, PostgreSQL 상태를 통합한 모니터링 API와 감사 로그를 추가한다. 이후 사용자 인증과 역할 기반 권한을 적용해 조회자, 운영자, 승인자의 작업 범위를 분리한다.
