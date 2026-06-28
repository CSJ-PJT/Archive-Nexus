package com.archivenexus.backend.task;
import com.archivenexus.backend.task.NexusTaskModels.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;import java.util.*;
@Service public class NexusTaskService {
 private final NexusTaskRepository tasks;private final NexusTaskLogRepository logs;private final TaskStateStore state;private final NexusTaskRunner runner;private final TaskExecutor executor;
 public NexusTaskService(NexusTaskRepository tasks,NexusTaskLogRepository logs,TaskStateStore state,NexusTaskRunner runner,@Qualifier("nexusTaskExecutor") TaskExecutor executor){this.tasks=tasks;this.logs=logs;this.state=state;this.runner=runner;this.executor=executor;}
 @Transactional public TaskResponse create(CreateTaskRequest r){Instant now=Instant.now();NexusTaskEntity t=tasks.save(new NexusTaskEntity("TASK-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(),r.title().trim(),r.type(),blank(r.factoryId()),blank(r.question()),value(r.requestedBy(),"operator"),r.maxAttempts()==null?3:r.maxAttempts(),now));logs.save(new NexusTaskLogEntity(t.id(),"INFO","작업이 생성됐습니다.",now));return response(t);}
 public List<TaskResponse> findAll(){return tasks.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();}
 public Optional<TaskDetailResponse> findById(String id){return tasks.findById(id).map(t->new TaskDetailResponse(response(t),findLogs(id)));}
 public List<TaskLogResponse> findLogs(String id){return logs.findAllByTaskIdOrderByCreatedAtAsc(id).stream().map(l->new TaskLogResponse(l.id(),l.taskId(),l.level(),l.message(),l.createdAt())).toList();}
 public Optional<TaskResponse> run(String id){Optional<NexusTaskEntity> started=state.start(id);started.ifPresent(t->{try{executor.execute(()->runner.execute(id));}catch(RuntimeException e){state.failIfRunning(id,"실행 큐가 작업을 수락하지 못했습니다: "+e.getMessage());}});return tasks.findById(id).map(this::response);}
 @Transactional public Optional<TaskResponse> cancel(String id){return tasks.findById(id).filter(t->t.status()==TaskStatus.PENDING||t.status()==TaskStatus.RUNNING).map(t->{t.cancel(Instant.now());logs.save(new NexusTaskLogEntity(id,"WARN","운영자가 작업을 중단했습니다.",Instant.now()));return response(t);});}
 @Transactional public Optional<TaskResponse> retry(String id){return tasks.findById(id).filter(t->(t.status()==TaskStatus.FAILED||t.status()==TaskStatus.CANCELLED)&&t.attemptCount()<t.maxAttempts()).map(t->{t.resetForRetry(Instant.now());logs.save(new NexusTaskLogEntity(id,"INFO","재시도를 위해 작업을 대기 상태로 전환했습니다.",Instant.now()));return response(t);});}
 private TaskResponse response(NexusTaskEntity t){return new TaskResponse(t.id(),t.title(),t.type(),t.factoryId(),t.question(),t.requestedBy(),t.status(),t.attemptCount(),t.maxAttempts(),t.resultSummary(),t.errorMessage(),t.createdAt(),t.startedAt(),t.completedAt(),t.updatedAt());}
 private String blank(String v){return v==null||v.isBlank()?null:v.trim();}private String value(String v,String f){return v==null||v.isBlank()?f:v.trim();}
}
