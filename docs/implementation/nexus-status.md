# Archive Nexus 구현 상태

## 완료

- Factory A/B/C 시뮬레이터와 제한된 factory executor
- 생산, 품질, 재고, 물류, 정비 데이터 생성과 이상 감지
- PostgreSQL `simulator_state` snapshot 및 파일 fallback
- RPA 생성, 승인, 반려와 ArchiveOS interaction 기록
- Manufacturing Orchestrator, Intent Router, 세 제조 전문 Agent와 통합 응답
- AI Query JPA 이력, Dashboard API, Prometheus/Grafana
- React 운영 화면과 실제 `/api` 연결

## 진행 중

- 실제 Discord webhook과 외부/ngrok 환경 검증

## 미구현

- 외부/ngrok 접속 검증

## 이번 브랜치에서 완료

- `nexus_tasks`, `nexus_task_logs` JPA/Flyway 영속화
- 작업 생성·조회·실행·취소·재시도·로그 API
- 제한된 TaskExecutor와 Manufacturing Orchestrator/Simulator Service 연결
- 실패 및 승인 필요 이벤트 전용 Discord adapter와 환경변수 설정
- Tasks UI, 실제 API 갱신, 상태별 실행 버튼과 로그 표시
- 820px 이하 단일 열 작업 화면

## 차단 요소

- 실제 Discord webhook과 ngrok 주소는 secret/외부 환경이므로 저장소에 포함할 수 없다.
- ArchiveOS 원격 Agent 실행은 현 단계 범위가 아니며 Nexus 내부 Orchestrator 실행을 유지한다.

## 다음 구현 순서

1. JPA 기반 운영 작업과 로그 모델, Flyway migration
2. 제한된 executor 기반 실행·취소·재시도 서비스
3. Manufacturing Orchestrator와 simulator tick 작업 연결
4. 실패/크리티컬 Discord 알림 adapter
5. Tasks API와 React 작업·로그 화면
6. API, DB, 알림, UI 및 모바일 회귀 테스트

## 검증 방법

```powershell
cd backend
.\gradlew.bat clean test --no-daemon
.\gradlew.bat bootJar --no-daemon

cd ..\frontend
npm run test
npm run build

cd ..
docker compose config
docker compose up --build -d
docker compose ps
```

런타임에서는 작업 생성 → 실행 → 로그 조회 → 최종 상태를 확인하고, 실패 작업 재시도와
대기 작업 취소를 검증한다. Discord는 테스트 webhook으로 크리티컬 이벤트만 전달되는지
확인하며 실제 webhook 값은 `ARCHIVE_NEXUS_DISCORD_WEBHOOK_URL` 환경변수로만 주입한다.
