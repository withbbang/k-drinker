package com.kdrinker.publish;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 게시는 한 번에 하나씩. 컨테이너 대기가 길 수 있어 분석 실행기와 분리한다. */
@Slf4j
@Configuration
public class PublishExecutorConfig {

	public static final String EXECUTOR = "publishExecutor";
	static final int QUEUE_CAPACITY = 100;

	@Bean(name = EXECUTOR)
	public ThreadPoolTaskExecutor publishExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(QUEUE_CAPACITY);
		executor.setThreadNamePrefix("publish-");
		// 커밋 뒤에 제출되므로 거부 예외를 던지지 않는다. 게시물은 PUBLISHING으로 남아 다음 기동 때 실패 처리된다.
		executor.setRejectedExecutionHandler((task, pool) -> log.warn("게시 대기열이 가득 차 작업을 버렸습니다. 다음 기동 때 실패로 복구됩니다."));
		return executor;
	}

	@Bean
	public PublishSleeper publishSleeper() {
		return duration -> Thread.sleep(duration.toMillis());
	}
}
