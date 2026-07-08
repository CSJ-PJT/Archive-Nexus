package com.archivenexus.backend.audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.Instant;import java.util.*;
@Service public class AuditService{
 private final AuditLogRepository repository;private final ObjectMapper mapper;
 public AuditService(AuditLogRepository repository,ObjectMapper mapper){this.repository=repository;this.mapper=mapper;}
 public void record(String actor,String action,String reason,String taskId,String correlationId,String workflowId,Map<String,?> details){try{repository.save(new AuditLogEntity(value(actor,"system"),action,reason,taskId,correlationId,workflowId,mapper.writeValueAsString(details==null?Map.of():details),Instant.now()));}catch(Exception ignored){}}
 public List<AuditLogEntity> recent(){return repository.findTop200ByOrderByOccurredAtDesc();}
 private String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
}
