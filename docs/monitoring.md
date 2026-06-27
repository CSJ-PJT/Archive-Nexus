# Archive Nexus Monitoring

Archive Nexus 모니터링은 애플리케이션 상태를 노출하는 Spring Boot Actuator, 지표를 수집하는 Prometheus, 운영 화면을 제공하는 Grafana로 구성한다.

## 구성

```text
Archive Nexus Backend
  └─ /actuator/health
  └─ /actuator/prometheus
          │ scrape every 5s
          ▼
     Prometheus :19090
          │ datasource
          ▼
       Grafana :13000
```

- Actuator는 `health`, `info`, `prometheus` endpoint만 외부에 노출한다.
- Prometheus는 Docker network의 `archive-nexus-backend:8080`을 수집한다.
- Grafana datasource와 dashboard는 컨테이너 시작 시 자동 provisioning된다.
- Prometheus와 Grafana 데이터는 named volume에 유지된다.

## 실행

```powershell
docker compose up --build -d
docker compose ps
```

접근 주소:

| 서비스 | URL | 확인 기준 |
|---|---|---|
| Backend health | `http://localhost:8080/actuator/health` | `status`가 `UP` |
| Metrics | `http://localhost:8080/actuator/prometheus` | `archive_nexus_*` 지표 존재 |
| Prometheus | `http://localhost:19090` | target `archive-nexus-backend`가 `UP` |
| Grafana | `http://localhost:13000` | 로그인 및 `Archive Nexus Operations` 조회 가능 |

Grafana 기본 계정은 `admin / archive_nexus_local`이다. 공유 환경에서는 반드시 `.env`로 비밀번호를 변경한다.

```dotenv
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me
```

## Custom Metrics

| Prometheus metric | Type | 의미 |
|---|---|---|
| `archive_nexus_simulator_running` | gauge | simulator 실행 여부, 실행 중 `1` |
| `archive_nexus_simulator_tick` | gauge | 현재 simulator tick |
| `archive_nexus_factory_count` | gauge | 가상 공장 수 |
| `archive_nexus_anomaly_count` | gauge | 시뮬레이터가 생성한 `FactoryAlert` 누적 수 |
| `archive_nexus_rpa_task_count` | gauge | ArchiveOS 연동용 RPA task 누적 수 |
| `archive_nexus_batch_snapshot_count` | gauge | batch snapshot 누적 수 |
| `archive_nexus_persistence_save_total` | counter | PostgreSQL runtime snapshot 저장 성공 횟수 |
| `archive_nexus_restore_source_total{source=...}` | counter | `postgresql`, `file`, `seed`별 프로세스 복구 횟수 |

모든 Micrometer 지표에는 `application="archive-nexus-backend"` 공통 tag가 추가된다. 현재 anomaly 지표의 권위 있는 원본은 이상 규칙이 생성한 `FactoryAlert` 목록이다.

## Grafana Dashboard

Provisioning 파일:

- datasource: `monitoring/grafana/provisioning/datasources/prometheus.yml`
- dashboard provider: `monitoring/grafana/provisioning/dashboards/archive-nexus.yml`
- dashboard JSON: `monitoring/grafana/dashboards/archive-nexus-operations.json`

기본 dashboard는 다음 항목을 제공한다.

- simulator running status
- current tick
- factory count
- anomaly count
- RPA task count
- batch snapshot count
- PostgreSQL persistence save count
- restore source count

## 운영 점검

1. `docker compose ps`에서 PostgreSQL과 backend가 `healthy`인지 확인한다.
2. backend `/actuator/prometheus`에 `archive_nexus_simulator_tick`이 존재하는지 확인한다.
3. Prometheus `/api/v1/targets`에서 scrape target health가 `up`인지 확인한다.
4. Grafana health API가 `ok`를 반환하는지 확인한다.
5. simulator 시작 후 tick, persistence save count, anomaly/RPA 지표가 갱신되는지 확인한다.

Prometheus target이 내려가 있으면 backend health, Docker network DNS, `/actuator/prometheus` 노출 설정 순서로 확인한다. Grafana에 데이터가 없으면 datasource provisioning과 Prometheus query 결과를 먼저 점검한다.
