package com.archivenexus.backend.archiveos;

import com.archivenexus.backend.domain.DomainModels.AlertSeverity;
import com.archivenexus.backend.domain.DomainModels.ArchiveOsInteraction;
import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.domain.DomainModels.RpaTask;
import com.archivenexus.backend.domain.DomainModels.RpaTaskStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MockArchiveOsClient implements ArchiveOsClient {
    private final List<ArchiveOsInteraction> interactions = new CopyOnWriteArrayList<>();

    @Override
    public void sendEvent(FactoryAlert alert) {
        record("SEND_EVENT", alert.factoryId(), alert.category() + ":" + alert.message());
    }

    @Override
    public List<String> requestRagAnalysis(FactoryAlert alert) {
        record("RAG_SEARCH", alert.factoryId(), alert.category());
        return switch (alert.category()) {
            case "QUALITY" -> List.of("품질 Lot 검사 기준서", "출하 보류 및 재작업 절차");
            case "MAINTENANCE" -> List.of("예지보전 진동/온도 임계치", "설비 정비 작업 표준서");
            case "INVENTORY" -> List.of("안전재고 보충 정책", "긴급 발주 승인 절차");
            case "LOGISTICS" -> List.of("납기 지연 대응 절차", "출하 우선순위 정책");
            default -> List.of("ArchiveOS 운영 관제 기본 절차");
        };
    }

    @Override
    public RpaTask createRpaTask(FactoryAlert alert, String recommendation, boolean approvalRequired) {
        record("RPA_TASK_CREATE", alert.factoryId(), recommendation);
        return new RpaTask(
                "RPA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                alert.factoryId(),
                approvalRequired ? RpaTaskStatus.APPROVAL_REQUIRED : RpaTaskStatus.COMPLETED,
                alert.message(),
                recommendation,
                approvalRequired,
                Instant.now()
        );
    }

    @Override
    public RpaTask updateRpaStatus(String taskId, String status) {
        record("RPA_STATUS_UPDATE", null, taskId + ":" + status);
        return null;
    }

    @Override
    public void requestApproval(RpaTask task) {
        record("APPROVAL_REQUEST", task.factoryId(), task.id());
    }

    @Override
    public void publishAlert(FactoryAlert alert) {
        record("ALERT_PUBLISH", alert.factoryId(), alert.message());
    }

    public boolean requiresApproval(FactoryAlert alert) {
        return alert.severity() == AlertSeverity.CRITICAL;
    }

    public List<ArchiveOsInteraction> interactions() {
        return interactions;
    }

    public void restoreInteractions(List<ArchiveOsInteraction> restoredInteractions) {
        interactions.clear();
        if (restoredInteractions != null) {
            interactions.addAll(restoredInteractions);
        }
    }

    private void record(String type, String factoryId, String payload) {
        interactions.add(new ArchiveOsInteraction(
                "AOS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                type,
                factoryId,
                payload,
                Instant.now()
        ));
    }
}
