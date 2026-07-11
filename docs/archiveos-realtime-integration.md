# ArchiveOS Realtime Integration · Archive-Nexus

ArchiveOS Console V3의 현재 Live Flow collector는 Nexus operations summary를 read-only로 집계한다. Nexus는 그 수집 경로와 호환되는 Runtime Mesh API를 제공하며, event 단위 수집기는 cursor pull 방식으로 확장할 수 있다.

1. `GET /api/runtime/status`로 `runtimeActive`, `pipelineStatus`, `lastWorkAt`, `lastEventAt`, `latestCursor`를 확인한다.
2. 최초 연결은 `GET /api/runtime-events/recent?limit=100`으로 시작한다.
3. 이후 `GET /api/runtime-events/recent?after={latestCursor}&limit=100`으로 증분 수집한다.
4. 세부 흐름은 correlation 또는 entity API로 보강한다.
5. `GET /api/operations/summary`, workforce/productivity/capacity summary를 별도 집계한다.

Nexus는 ArchiveOS의 가용성을 핵심 제조 처리 조건으로 삼지 않는다. ArchiveOS가 UNAVAILABLE인 경우에도 Runtime Event와 Outbox는 Nexus에 보존되며, 재연결한 ArchiveOS는 summary 재집계 또는 cursor 기반 증분 수집으로 다시 동기화할 수 있다.

> 모든 값은 Synthetic Runtime Data다. API 응답에 실제 고객·직원·결제·주소·비밀값을 포함하지 않는다.
