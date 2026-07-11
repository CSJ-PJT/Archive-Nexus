# Runtime Operations Runbook · Archive-Nexus

## 정상 확인

```powershell
curl.exe http://localhost:8080/api/runtime/status
curl.exe http://localhost:8080/api/runtime-events/recent?limit=20
curl.exe http://localhost:8080/api/operations/summary
```

`autoRunEnabled=true`이고 `pipelineStatus=LIVE`이면 local/demo autorun이 제한된 synthetic 제조 work를 수행한다. `lastWorkAt`과 `lastEventAt`은 최근 작업 시각을, `eventsProducedLastTick`과 `eventsConsumedLastTick`은 마지막 tick 처리량을 나타낸다.

## 정체 또는 backlog

1. `GET /api/runtime/status`에서 `schedulerStatus`, `degradedReason`, `backlogCount`를 확인한다.
2. `GET /api/capacity/summary`와 `GET /api/workforce/summary`에서 bottleneck role 및 used/remaining capacity를 확인한다.
3. `GET /api/runtime-events/recent`에서 `CAPACITY_SHORTAGE_DETECTED`, `BACKLOG_INCREASED`, `PRODUCTION_DELAYED` projection을 확인한다.
4. workforce allocation 또는 workday 실행은 명시적인 POST 작업이며 summary GET으로 실행되지 않는다.

## ArchiveOS 미가용

ArchiveOS가 꺼져도 Nexus의 Market inbound, workforce, simulator, outbox는 계속 동작한다. Runtime Mesh는 기본 pull 방식이므로 ArchiveOS 복구 뒤 최근 이벤트 또는 cursor 증분 조회로 재동기화한다. Nexus는 ArchiveOS의 응답 실패 때문에 제조 트랜잭션을 rollback하지 않는다.

## 안전 한계

`archive.runtime.max-events-per-tick`과 `archive.runtime.max-backlog-per-tick`은 tick당 synthetic 작업량을 제한한다. tick별 idempotencyKey, scheduler lock, hopCount/maxHop guard로 중복·순환 이벤트 폭증을 막는다.
