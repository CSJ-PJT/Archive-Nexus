package com.archivenexus.backend.web;
import com.archivenexus.backend.task.NexusTaskModels.*;
import com.archivenexus.backend.task.NexusTaskService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/tasks") public class NexusTaskController {
 private final NexusTaskService tasks;public NexusTaskController(NexusTaskService tasks){this.tasks=tasks;}
 @PostMapping ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(tasks.create(r));}
 @GetMapping List<TaskResponse> all(){return tasks.findAll();}
 @GetMapping("/{id}") ResponseEntity<TaskDetailResponse> one(@PathVariable String id){return ResponseEntity.of(tasks.findById(id));}
 @GetMapping("/{id}/logs") List<TaskLogResponse> logs(@PathVariable String id){return tasks.findLogs(id);}
 @PostMapping("/{id}/run") ResponseEntity<TaskResponse> run(@PathVariable String id){return ResponseEntity.of(tasks.run(id));}
 @PostMapping("/{id}/sync") ResponseEntity<TaskResponse> sync(@PathVariable String id){return ResponseEntity.of(tasks.sync(id));}
 @PostMapping("/{id}/cancel") ResponseEntity<TaskResponse> cancel(@PathVariable String id){return ResponseEntity.of(tasks.cancel(id));}
 @PostMapping("/{id}/retry") ResponseEntity<TaskResponse> retry(@PathVariable String id){return ResponseEntity.of(tasks.retry(id));}
}
