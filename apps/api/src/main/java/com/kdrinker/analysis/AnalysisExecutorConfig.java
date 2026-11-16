package com.kdrinker.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 분석은 한 번에 하나씩(메모리 -Xmx512m, Claude 호출 직렬화). 대기열이 차면 버리고 다음 기동 때 복구가 다시 넣는다.
 * 이 빈이 있으면 Boot가 기본 실행기를 만들지 않으므로 application.yml의 {@code spring.task.execution.mode=force}로
 * applicationTaskExecutor를 따로 남긴다. 이름 없는 {@code @Async}는 그쪽을 쓴다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AnalysisExecutorConfig {

	public static final String EXECUTOR = "analysisExecutor";
	static final int QUEUE_CAPACITY = 100;

	@Bean(name = EXECUTOR)
	public ThreadPoolTaskExecutor analysisExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(QUEUE_CAPACITY);
		executor.setThreadNamePrefix("analysis-");
		// 접수 커밋 뒤에 제출되므로 거부 예외를 던지면 이미 끝난 접수 요청이 실패로 보인다. 로그만 남긴다(게시물은 RECEIVED로 남음).
		executor.setRejectedExecutionHandler((task, pool) -> log.warn("분석 대기열이 가득 차 작업을 버렸습니다. 다음 기동 때 복구됩니다."));
		return executor;
	}
}
