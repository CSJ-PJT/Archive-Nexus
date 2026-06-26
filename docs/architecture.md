# Archive Nexus 아키텍처

Archive Nexus는 ArchiveOS에 내장되지 않는 별도 제조 AX 애플리케이션이다. 제조 도메인 상태와 시뮬레이션은 Archive Nexus가 소유하고, AI Runtime, RAG, RPA 승인, Batch, 관제 이벤트는 ArchiveOS API 또는 SDK를 통해 요청한다.

## 구성

- `backend`: Spring Boot 기반 API와 시뮬레이터 런타임
- `frontend`: React 기반 공장 관제 화면
- `backend/src/main/resources/schema.sql`: PostgreSQL 기준 도메인 스키마
- `MockArchiveOsClient`: ArchiveOS 실제 연동 전까지 사용하는 adapter
- `docker-compose.yml`: backend, frontend, postgres 로컬 실행 구성

## 데이터 흐름

1. 시뮬레이터가 Factory A/B/C의 센서, 생산, 품질, 재고, 물류 데이터를 생성한다.
2. 각 factory tick은 backend executor에서 병렬로 실행된다.
3. 규칙 기반 임계치가 정상 데이터와 이상 이벤트를 구분한다.
4. 정상 데이터는 API 조회와 배치 집계 대상으로 유지한다.
5. 이상 이벤트만 ArchiveOS adapter로 전달된다.
6. adapter는 RAG 근거를 조회하고 RPA Task를 생성한다.
7. Critical 이벤트는 `APPROVAL_REQUIRED` 상태로 남아 승인 API를 기다린다.
8. 정상 운영 데이터는 5 tick마다 Batch Snapshot으로 집계된다.

## RPA 개입 흐름

```text
Factory Event
→ Rule Detection
→ FactoryAlert
→ ArchiveOsClient.requestRagAnalysis
→ ArchiveOsClient.createRpaTask
→ approval_required 또는 completed
```

## 관측 API

- `GET /api/batch/snapshots`: Spring Batch 역할의 주기 집계 결과를 조회한다.
- `GET /api/archiveos/interactions`: ArchiveOS mock adapter가 받은 이벤트, RAG 조회, RPA 생성, 승인 요청을 조회한다.

이 두 API를 함께 보면 정상 데이터는 배치 집계로만 흐르고, 이상 이벤트가 발생한 경우에만 ArchiveOS AI/RAG/RPA 경로가 호출되는지 확인할 수 있다.

`GET /api/simulator/status`의 `parallelWorkerCount`는 마지막 tick에서 병렬 실행된 factory worker 수를 보여준다.
