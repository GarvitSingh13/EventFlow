package com.eventflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "deliveryTaskExecutor", destroyMethod = "close")
    public ExecutorService deliveryTaskExecutor() {
        // High-concurrency Java 21 Virtual Threads for concurrent outbound webhook dispatching
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
