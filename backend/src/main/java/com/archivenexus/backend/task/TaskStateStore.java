package com.archivenexus.backend.task;
import com.archivenexus.backend.task.NexusTaskModels.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
@Service public class TaskStateStore {
 private final NexusTaskRepository tasks; private final NexusTaskLogRepository logs;
 public TaskStateStore(NexusTaskRepository tasks,NexusTaskLogRepository logs){this.tasks=tasks;this.logs=logs;}
 @Transactional public Optional<NexusTaskEntity> start(String id){return tasks.findById(id).filter(t->t.status()==TaskStatus.PENDING).map(t->{t.start(Instant.now());logs.save(new NexusTaskLogEntity(id,"INFO","작업 실행을 시작했습니다.",Instant.now()));return t;});}
 @Transactional public void log(String id,String level,String message){logs.save(new NexusTaskLogEntity(id,level,message,Instant.now()));}
 @Transactional public void succeedIfRunning(String id,String summary){tasks.findById(id).filter(t->t.status()==TaskStatus.RUNNING).ifPresent(t->{t.succeed(summary,Instant.now());logs.save(new NexusTaskLogEntity(id,"INFO","작업이 성공적으로 완료됐습니다.",Instant.now()));});}
 @Transactional public void failIfRunning(String id,String message){tasks.findById(id).filter(t->t.status()==TaskStatus.RUNNING).ifPresent(t->{t.fail(message,Instant.now());logs.save(new NexusTaskLogEntity(id,"ERROR","작업 실패: "+message,Instant.now()));});}
}
