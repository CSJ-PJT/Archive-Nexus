# Archive Nexus Schema

Archive Nexus keeps the existing domain schema for factory, production, quality, inventory, logistics, maintenance, RPA, batch, and ArchiveOS interaction data.

Schema changes are managed by Flyway migrations under `backend/src/main/resources/db/migration`. `V1__initialize_archive_nexus_schema.sql` is idempotent so it supports both a new database and a pre-Flyway local volume. For an existing non-empty schema, Flyway records baseline version `0` and then applies V1 without deleting data.

For simulator runtime recovery, the operational persistence table is `simulator_state`.

## simulator_state

`simulator_state` stores one current runtime snapshot with id `archive-nexus-runtime`.

| Column | Purpose |
|---|---|
| `id` | Snapshot key |
| `running` | Simulator running flag |
| `tick` | Current simulator tick |
| `last_parallel_worker_count` | Factory workers used by the last tick |
| `factories_json` | Factory state |
| `sensor_metrics_json` | Sensor metrics |
| `production_orders_json` | Production state |
| `lots_json` | Lot state |
| `quality_inspections_json` | Quality state |
| `inventory_items_json` | Inventory balances |
| `inventory_transactions_json` | Inventory transaction history |
| `logistics_shipments_json` | Logistics state |
| `maintenance_events_json` | Maintenance state |
| `alerts_json` | Factory alerts |
| `rpa_tasks_json` | RPA tasks |
| `batch_snapshots_json` | Batch snapshots |
| `archiveos_interactions_json` | ArchiveOS adapter interaction log |
| `saved_at` | Last DB save timestamp |

The file snapshot at `data/archive-nexus-state.json` remains as fallback/local backup. Restore order is PostgreSQL first, file snapshot second, seed data last.

## ai_query_history

`V2__add_ai_query_history.sql`은 Manufacturing Orchestrator 실행 이력을 저장한다.

| Column | Purpose |
|---|---|
| `query_id` | AI Query correlation key |
| `original_question` | 운영자 자연어 질문 |
| `requested_by` | 요청 사용자 |
| `selected_factory_id` | 선택 공장, 전체 범위이면 null |
| `routed_intents_json` | 복수 routing Intent |
| `invoked_agents_json` | 호출 Agent 이름 |
| `agent_results_json` | Agent별 상태, 근거, 권장 조치, 실행 시간 |
| `final_answer` | 통합 운영 판단 |
| `evidence_json` | 통합 근거 |
| `recommended_actions_json` | 통합 권장 조치 |
| `confidence` | 통합 신뢰도 |
| `execution_status` | 완료, 부분 성공, 데이터 부족, 실패 상태 |
| `execution_time_ms` | 전체 실행 시간 |
| `error_message` | 실패 Agent 또는 실행 오류 |
| `rpa_task_id` | 연결된 Multi-Agent RPA task |

## Nexus 운영 작업

`nexus_tasks`는 `MANUFACTURING_QUERY`, `SIMULATOR_TICK`, `SCENARIO_RECOVERY` 작업과
`DRAFT`, `PENDING`, `ANALYZING`, `WAITING_APPROVAL`, `APPROVED`, `RUNNING`, `VERIFYING`,
`SUCCESS`, `FAILED`, `REJECTED`, `CANCELLED`, `RETRY_REQUESTED` 상태를 저장한다.
결과에는 `evidence_json`, `recommendation_json`, `confidence`, `correlation_id`, `workflow_id`,
`approval_id`, `rpa_task_id`가 포함된다.
`nexus_task_logs`는 작업별 INFO/WARN/ERROR 로그를 시간순으로 저장한다. 두 테이블은 simulator
snapshot과 분리된 JPA/Flyway 영속 모델이다.

## Audit와 점진적 Aggregate 분리

`nexus_audit_logs`는 actor, action, reason, task/correlation/workflow ID, 세부 JSON과 발생 시각을 저장한다.
`production_aggregates`, `inspection_aggregates`, `maintenance_aggregates`, `approval_aggregates`는
현재 simulator snapshot에서 계산한 최신 bounded projection이다. 원본 이력 전체를 복제하지 않으며
향후 독립 Domain Aggregate로 전환할 때 호환 경계로 사용한다.

현재 프로젝트의 runtime JSON 저장 규칙과 동일하게 구조화 목록은 text JSON으로 저장하며, 기존 DB 데이터를 삭제하거나 `simulator_state` 구조를 변경하지 않는다.
