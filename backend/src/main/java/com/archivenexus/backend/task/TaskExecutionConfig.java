package com.archivenexus.backend.task;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration public class TaskExecutionConfig {
 @Bean("nexusTaskExecutor") TaskExecutor nexusTaskExecutor(){ThreadPoolTaskExecutor e=new ThreadPoolTaskExecutor();e.setCorePoolSize(2);e.setMaxPoolSize(4);e.setQueueCapacity(50);e.setThreadNamePrefix("nexus-task-");e.initialize();return e;}
}
