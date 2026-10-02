package com.example.smarttask.auth;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class AccountMailConfiguration {

	@Bean(name = "accountMailExecutor")
	ThreadPoolTaskExecutor accountMailExecutor(
			@Value("${smarttask.mail.worker-count:2}") int workerCount,
			@Value("${smarttask.mail.queue-capacity:100}") int queueCapacity) {
		if (workerCount < 1 || workerCount > 16 || queueCapacity < 1 || queueCapacity > 10_000) {
			throw new IllegalArgumentException("Invalid account email executor configuration");
		}
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(workerCount);
		executor.setMaxPoolSize(workerCount);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("account-mail-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(5);
		return executor;
	}
}
