<p align="center">
  <img src="docs/brand/archive-nexus-lockup.svg" width="900" alt="Archive Nexus" />
</p>

# Archive-Nexus

> **AI 기반 가상 공장과 지능형 RPA를 위한 제조 AX 플랫폼**

Archive-Nexus는 ArchiveOS 위에서 동작하는 제조 시뮬레이션 애플리케이션입니다. Factory A/B/C의 생산, 품질, 재고, 물류와 정비 데이터를 생성하고 운영 대시보드에서 상태를 관찰합니다.

## 현재 구현 상태

- Spring Boot 기반 제조 시뮬레이터
- React 기반 운영 대시보드
- 생산·재고·품질·정비·물류 도메인
- PostgreSQL 우선 저장과 JSON snapshot fallback
- RPA 작업 생성, 승인과 반려 흐름
- PostgreSQL 기반 운영 작업 생성·실행·취소·재시도와 실행 로그
- Actuator, Prometheus와 Grafana 모니터링
- Spring AI 제조 Orchestrator
- Production, Quality, Maintenance 전문 Agent

ArchiveOS 실제 연동, 인증과 역할 기반 권한, 감사 로그 고도화는 후속 작업입니다.

## ArchiveOS와의 관계

```text
ArchiveOS
├── AI Runtime / RAG
├── Batch / Workflow
├── RPA / Approval
└── Observability
        │
        ▼
Archive-Nexus
├── Virtual Factories
├── Production / Quality
├── Inventory / Logistics
├── Maintenance
└── Manufacturing AI
```

ArchiveOS는 공통 AI 실행 환경을 담당하고 Archive-Nexus는 제조 도메인 로직을 담당합니다.

## 가상 공장

- **Factory A**: 자동차 부품, 안정적인 생산과 낮은 불량률
- **Factory B**: 배터리 모듈, 센서 이상과 예지보전
- **Factory C**: 전장 부품, Lot 품질 추적과 출하 보류

## 운영 화면

- Overview, Factories, Production, Inventory
- Quality, Maintenance, Logistics, RPA
- Manufacturing AI, Settings

운영 화면은 제조 API를 주기적으로 조회하고 일부 요청이 실패하면 마지막 정상 데이터를 유지하면서 오류 상태를 표시합니다.

## 기술 구성

- Java 21, Spring Boot 3, Spring Data JPA, Spring AI
- React, Vite, TypeScript, Nginx
- PostgreSQL, Flyway
- Docker, Docker Compose
- Actuator, Prometheus, Grafana

## 로컬 실행

```powershell
docker compose up --build -d
docker compose ps
```

| 항목 | 주소 |
| --- | --- |
| Archive Nexus | `http://localhost:15173` |
| Backend Health | `http://localhost:8080/actuator/health` |
| Prometheus | `http://localhost:19090` |
| Grafana | `http://localhost:13000` |

## 운영 작업 API

`POST /api/tasks`, `GET /api/tasks`, `GET /api/tasks/{id}`, `GET /api/tasks/{id}/logs`,
`POST /api/tasks/{id}/run`, `POST /api/tasks/{id}/cancel`, `POST /api/tasks/{id}/retry`를 제공한다.

ArchiveOS 상태는 `GET /api/archiveos/status`에서 확인한다. 기본 연동 주소는
`http://host.docker.internal:4000`이며, ArchiveOS 장애는 Nexus 제조 데이터 API와 화면 로딩을
중단시키지 않고 `DEGRADED` 또는 `UNAVAILABLE` 상태로 표시된다.
작업은 `PENDING → RUNNING → SUCCESS | FAILED | CANCELLED`로 관리하며 Agent 분석은 기존
Manufacturing Orchestrator를 호출한다. 실패 또는 승인 필요 알림의 Discord webhook은
`ARCHIVE_NEXUS_DISCORD_WEBHOOK_URL` 환경변수로만 주입한다.

## 문서

- [운영 대시보드](docs/operations-dashboard.md)
- [모니터링](docs/monitoring.md)
- [Multi-Agent 구조](docs/multi-agent-architecture.md)
- [브랜드 가이드](docs/brand/README.md)

## Roadmap

- [x] 가상 공장과 제조 데이터 생성
- [x] PostgreSQL 저장과 snapshot fallback
- [x] 제조 운영 대시보드
- [x] RPA 승인 흐름
- [x] Prometheus/Grafana 기반
- [x] Spring AI 제조 Agent 기반
- [ ] ArchiveOS 실제 연동 완성
- [ ] 감사 로그 고도화
- [ ] 사용자 인증과 역할 기반 권한

## Brand

> **Intelligence. Automation. Convergence.**

Archive Nexus는 ArchiveOS와 동일한 공식 `A` 마크를 사용합니다. `NEXUS`는 청록색으로 구분하고 `ARCHIVE`와 충분히 떨어뜨려 `E`와 `N`이 겹치지 않도록 수정했습니다.

README, Sidebar, Header와 브라우저 아이콘은 동일한 공식 마크를 사용합니다.

## License

라이선스 정책은 프로젝트 운영 방침에 따라 추후 정의합니다.
