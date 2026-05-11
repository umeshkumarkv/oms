package com.brokerdealer.marketdataservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduling and async execution.
 *
 * Spring Boot 3.2 with spring.threads.virtual.enabled=true automatically
 * provisions a virtual-thread-backed SimpleAsyncTaskExecutor as the default
 * @Async executor — no custom Executor bean required.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
