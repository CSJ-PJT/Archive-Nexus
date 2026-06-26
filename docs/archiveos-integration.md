# ArchiveOS 연동 계획

Archive Nexus는 ArchiveOS를 직접 포함하지 않는다. `ArchiveOsClient` interface를 통해 플랫폼 기능을 호출한다.

## Adapter 계약

- `sendEvent()`: 제조 이벤트 또는 이상 알림 전송
- `requestRagAnalysis()`: RAG 근거 조회
- `createRpaTask()`: 지능형 RPA 작업 생성
- `updateRpaStatus()`: RPA 상태 동기화
- `requestApproval()`: 승인 게이트 요청
- `publishAlert()`: 관제 알림 발행

## Mock 전략

초기 MVP에서는 `MockArchiveOsClient`가 RAG 근거와 RPA Task 생성을 대체한다. 실제 ArchiveOS API가 준비되면 동일 interface를 구현하는 HTTP 또는 SDK adapter를 추가한다.

## 향후 연동

- ArchiveOS AI Runtime 분석 요청
- Spring Batch 실행 이력 저장
- RAG index 기반 작업 표준서 검색
- Approval Gate 승인/반려 webhook
- RPA 실행 결과와 재시도 로그 저장

