package com.archivenexus.backend.platform;

import com.archivenexus.backend.archiveos.ArchiveOsHealthService;
import com.archivenexus.backend.platform.PlatformManifestModels.Capability;
import com.archivenexus.backend.platform.PlatformManifestModels.ContractEndpoint;
import com.archivenexus.backend.platform.PlatformManifestModels.Dependency;
import com.archivenexus.backend.platform.PlatformManifestModels.PlatformManifest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PlatformManifestService {
    private final ArchiveOsHealthService archiveOsHealthService;
    private final Optional<BuildProperties> buildProperties;
    private final String environment;
    private final String repository;
    private final String archiveOsBaseUrl;
    private final String backendAtlasUrl;

    public PlatformManifestService(
            ArchiveOsHealthService archiveOsHealthService,
            Optional<BuildProperties> buildProperties,
            @Value("${archive-nexus.platform.environment:local}") String environment,
            @Value("${archive-nexus.platform.repository:https://github.com/CSJ-PJT/Archive-Nexus}") String repository,
            @Value("${archive-nexus.archiveos.base-url:http://host.docker.internal:4000}") String archiveOsBaseUrl,
            @Value("${archive-nexus.platform.backend-atlas-url:https://github.com/CSJ-PJT/Backend-Atlas}") String backendAtlasUrl
    ) {
        this.archiveOsHealthService = archiveOsHealthService;
        this.buildProperties = buildProperties;
        this.environment = environment;
        this.repository = repository;
        this.archiveOsBaseUrl = archiveOsBaseUrl;
        this.backendAtlasUrl = backendAtlasUrl;
    }

    public PlatformManifest manifest() {
        var archiveOsStatus = archiveOsHealthService.status();
        return new PlatformManifest(
                "archive-nexus",
                "Archive Nexus",
                "Archive Suite",
                "Manufacturing Industry Application",
                buildProperties.map(BuildProperties::getVersion).orElse("0.0.1-SNAPSHOT"),
                "industry-app-contract/v1",
                environment,
                repository,
                "제조 도메인 데이터, 시뮬레이션, 이상 감지, 멀티 에이전트 분석과 실행 근거를 소유하는 ArchiveOS 위의 첫 Industry App입니다.",
                capabilities(),
                endpoints(),
                dependencies(archiveOsStatus.status()),
                List.of("production", "quality", "maintenance", "inventory", "logistics", "manufacturing-ai", "simulator"),
                List.of(
                        "ArchiveOS가 DEGRADED 또는 UNAVAILABLE이어도 제조 조회와 시뮬레이터 상태 API는 계속 응답합니다.",
                        "실제 제조 상태 변경은 Agent가 직접 수행하지 않고 Task, Approval, RPA 계약을 통해 실행합니다.",
                        "실행 결과는 evidence, recommendation, confidence, correlationId, workflowId, approvalId를 포함합니다.",
                        "누적 데이터 환경에서도 simulator start/stop은 빠른 acknowledgement를 우선합니다."
                ),
                archiveOsStatus,
                Instant.now()
        );
    }

    private List<Capability> capabilities() {
        return List.of(
                new Capability("manufacturing-simulator", "Manufacturing Simulator", "Factory A/B/C의 생산, 품질, 정비, 재고, 물류 상태를 생성하고 영속화합니다.", "ACTIVE"),
                new Capability("multi-agent-orchestrator", "Manufacturing Multi-Agent", "Production, Quality, Maintenance, Inventory, Logistics, Cross Domain Agent를 라우팅하고 통합 판단을 생성합니다.", "ACTIVE"),
                new Capability("workflow-contract", "ArchiveOS Workflow Contract", "Nexus Event → ArchiveOS Workflow → Approval → Nexus Action → Result Callback 흐름을 지원합니다.", "ACTIVE"),
                new Capability("rpa-approval-bridge", "RPA Approval Bridge", "위험 조치는 직접 실행하지 않고 RPA Task와 승인 흐름으로 전달합니다.", "ACTIVE"),
                new Capability("observability", "Operations Observability", "Prometheus, Grafana, Dashboard, Audit Log로 운영 상태를 관측합니다.", "ACTIVE")
        );
    }

    private List<ContractEndpoint> endpoints() {
        return List.of(
                new ContractEndpoint("GET", "/api/platform/manifest", "Nexus의 제품 정체성, 역량, 계약, 의존성을 노출합니다.", "Archive Nexus"),
                new ContractEndpoint("GET", "/api/archiveos/status", "ArchiveOS 연결 상태를 AVAILABLE/DEGRADED/UNAVAILABLE로 제공합니다.", "Archive Nexus"),
                new ContractEndpoint("POST", "/api/tasks", "제조 운영 작업 초안을 생성합니다.", "Archive Nexus"),
                new ContractEndpoint("POST", "/api/tasks/{id}/run", "ArchiveOS Workflow를 생성하고 Nexus 작업을 실행합니다.", "Archive Nexus"),
                new ContractEndpoint("POST", "/api/tasks/{id}/sync", "ArchiveOS 승인 상태를 Nexus 작업 상태로 동기화합니다.", "Archive Nexus"),
                new ContractEndpoint("POST", "/api/ai/query", "자연어 제조 질문을 Agent 기반 운영 판단으로 변환합니다.", "Archive Nexus"),
                new ContractEndpoint("POST", "/api/simulator/start", "시뮬레이터 실행 상태를 시작으로 전환합니다.", "Archive Nexus"),
                new ContractEndpoint("POST", "/api/simulator/stop", "시뮬레이터 실행 상태를 중지로 전환합니다.", "Archive Nexus")
        );
    }

    private List<Dependency> dependencies(String archiveOsStatus) {
        return List.of(
                new Dependency("ArchiveOS", "Workflow, Approval, History, RPA 관제", archiveOsBaseUrl, archiveOsStatus),
                new Dependency("Backend Atlas", "Archive Suite 구현 지식과 포트폴리오 학습 표면", backendAtlasUrl, "REFERENCE")
        );
    }
}
