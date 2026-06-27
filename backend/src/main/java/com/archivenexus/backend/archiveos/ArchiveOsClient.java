package com.archivenexus.backend.archiveos;

import com.archivenexus.backend.domain.DomainModels.FactoryAlert;
import com.archivenexus.backend.domain.DomainModels.RpaTask;

import java.util.List;

public interface ArchiveOsClient {
    void sendEvent(FactoryAlert alert);

    List<String> requestRagAnalysis(FactoryAlert alert);

    RpaTask createRpaTask(FactoryAlert alert, String recommendation, boolean approvalRequired);

    RpaTask updateRpaStatus(String taskId, String status);

    void requestApproval(RpaTask task);

    void publishAlert(FactoryAlert alert);

    void recordInteraction(String type, String factoryId, String payload);
}
