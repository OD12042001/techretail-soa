package com.techretail.techretail_gateway.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecutor acotado dedicado a la auditoria: cola finita para que una base de
 * datos lenta no consuma memoria sin limite; al saturarse se rechaza la tarea.
 */
@Configuration
public class AuditAsyncConfig {

    @Bean(name = "auditExecutor")
    Executor auditExecutor() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setThreadNamePrefix("audit-");
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(4);
        ejecutor.setQueueCapacity(1000);
        ejecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(5);
        return ejecutor;
    }
}
