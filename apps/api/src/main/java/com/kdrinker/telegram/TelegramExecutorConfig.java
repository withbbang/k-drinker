package com.kdrinker.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 텔레그램 수신 처리·전송을 한 줄로 세우는 실행기. {@code @EnableAsync}는 AnalysisExecutorConfig가 켠다. */
@Slf4j
@Configuration
public class TelegramExecutorConfig {

	public static final String EXECUTOR = "telegramExecutor";
	static final int QUEUE_CAPACITY = 100;

	@Bean(name = EXECUTOR)
	public ThreadPoolTaskExecutor telegramExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(QUEUE_CAPACITY);
		executor.setThreadNamePrefix("telegram-");
		executor.setRejectedExecutionHandler((task, pool) -> log.warn("텔레그램 대기열이 가득 차 작업을 버렸습니다."));
		return executor;
	}

}
