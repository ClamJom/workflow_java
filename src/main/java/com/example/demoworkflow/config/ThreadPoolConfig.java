package com.example.demoworkflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("workflow")
    public TaskExecutor workflowThreadExecutor(){
        ThreadFactory factory = Thread.ofVirtual()
                .name("workflow_virtual_thread", 0)
                .factory();
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(factory));
    }
}
