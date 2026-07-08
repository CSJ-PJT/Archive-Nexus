package com.archivenexus.backend.archiveos;

import com.archivenexus.backend.task.NexusTaskEntity;
import java.util.List;import java.util.Map;

public interface ArchiveOsWorkflowClient {
    WorkflowRef create(NexusTaskEntity task);
    WorkflowRef requestApproval(String workflowId);
    WorkflowRef get(String workflowId);
    void callback(String workflowId, WorkflowCallback callback);
    List<Map<String,Object>> events(String workflowId);

    record WorkflowRef(String id,String status,String approvalId){}
    record WorkflowCallback(String status,String summary,List<Map<String,Object>> evidence,List<String> recommendation,
                            Double confidence,String correlationId,String sourceTaskId,String approvalId){}
}
