# 시뮬레이션 모델

데이터 생성기는 완전 랜덤이 아니라 원인과 결과가 연결된 규칙 기반 흐름을 사용한다. 동일 seed에서는 유사한 운영 패턴을 재현할 수 있다.

## 정상 패턴

- 생산량은 목표 대비 약 78%~105% 범위에서 생성된다.
- Factory A는 안정적인 자동차 부품 생산을 기본으로 한다.
- Factory B는 설비 압력이 높아 진동, 온도, 전류 이상이 더 자주 발생한다.
- Factory C는 Lot 품질 변동이 더 크다.

## 이상 패턴

- 설비 진동/온도/전류가 설비별 임계치를 넘으면 `MAINTENANCE` 알림이 발생한다.
- 불량률이 3% 이상이면 `QUALITY` 알림과 출하 보류가 발생한다.
- 재고가 안전재고 이하로 내려가면 `INVENTORY` 알림이 발생한다.
- 생산량 급감 또는 확률 조건으로 납기 지연이 발생하면 `LOGISTICS` 알림이 발생한다.

## 생성 주기

현재 MVP는 `archive-nexus.simulator.tick-delay-ms` 설정값으로 tick을 생성한다. 기본값은 5초이며, `POST /api/simulator/start` 이후 스케줄러가 Codex 개입 없이 계속 데이터를 만든다.

각 tick에서 Factory A/B/C는 executor 기반 worker로 병렬 실행된다. 마지막 tick에서 사용된 worker 수는 `GET /api/simulator/status`의 `parallelWorkerCount`로 확인한다.

## Batch Snapshot

5 tick마다 Spring Batch 역할의 집계 스냅샷을 만든다.

- 전체 공장 수
- 생산 주문 수
- 누적 생산량
- 평균 불량률
- 알림 수
- 승인 대기 RPA 수

스냅샷은 `GET /api/batch/snapshots`에서 확인한다.
