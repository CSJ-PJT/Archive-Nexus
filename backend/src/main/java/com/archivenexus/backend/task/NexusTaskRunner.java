package com.archivenexus.backend.task;
import com.archivenexus.backend.ai.ManufacturingAiModels.*;
import com.archivenexus.backend.ai.ManufacturingOrchestrator;
import com.archivenexus.backend.notification.DiscordCriticalNotifier;
import com.archivenexus.backend.service.NexusStateService;
import org.springframework.stereotype.Service;
import java.util.Map;
@Service public class NexusTaskRunner {
 private final NexusTaskRepository tasks;private final TaskStateStore state;private final ManufacturingOrchestrator orchestrator;private final NexusStateService nexus;private final DiscordCriticalNotifier notifier;
 public NexusTaskRunner(NexusTaskRepository tasks,TaskStateStore state,ManufacturingOrchestrator orchestrator,NexusStateService nexus,DiscordCriticalNotifier notifier){this.tasks=tasks;this.state=state;this.orchestrator=orchestrator;this.nexus=nexus;this.notifier=notifier;}
 public void execute(String id){NexusTaskEntity t=tasks.findById(id).orElse(null);if(t==null||t.status()!=NexusTaskModels.TaskStatus.RUNNING)return;try{String summary=switch(t.type()){case MANUFACTURING_QUERY->executeQuery(t);case SIMULATOR_TICK->executeTick(t);};state.succeedIfRunning(id,summary);}catch(RuntimeException e){String m=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();state.failIfRunning(id,m);notifier.notifyCritical("Archive Nexus 작업 실패",t.title()+" ("+id+"): "+m);}}
 private String executeQuery(NexusTaskEntity t){if(t.question()==null||t.question().isBlank())throw new IllegalArgumentException("MANUFACTURING_QUERY 작업에는 question이 필요합니다.");state.log(t.id(),"INFO","Manufacturing Orchestrator에 분석을 요청합니다.");AiQueryResponse r=orchestrator.execute(new AiQueryRequest(t.question(),t.factoryId(),null,t.requestedBy(),Map.of("sourceTaskId",t.id())));state.log(t.id(),"INFO","AI Query "+r.queryId()+" 완료 · Intent "+r.routedIntents());if(r.approvalRequired())notifier.notifyCritical("Archive Nexus 승인 필요",t.title()+" · RPA "+r.rpaTaskId());if("INSUFFICIENT_DATA".equals(r.executionStatus()))throw new IllegalStateException("판단할 데이터가 부족합니다.");return r.answer();}
 private String executeTick(NexusTaskEntity t){state.log(t.id(),"INFO","Simulator tick 생성을 요청합니다.");nexus.generateTick();return "Simulator tick "+nexus.status().tick()+" 생성 완료";}
}
