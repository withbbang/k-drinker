package com.kdrinker.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/** 분석 전용 실행기를 등록해도 Boot 기본 실행기(applicationTaskExecutor)가 따로 남아 이름 없는 @Async가 분석 스레드를 쓰지 않는다. */
@SpringBootTest
class AnalysisExecutorConfigTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void 기본_실행기가_분석_실행기와_따로_있다() {
		// when
		Object analysis = context.getBean(AnalysisExecutorConfig.EXECUTOR);
		Object application = context.getBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME);

		// then
		assertThat(application).isNotSameAs(analysis);
	}

	@Test
	void 이름_없는_Async는_기본_실행기를_쓴다() {
		// when
		Executor unqualified = context.getBean(AsyncConfigurer.class).getAsyncExecutor();

		// then
		assertThat(unqualified)
				.isSameAs(context.getBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME))
				.isNotSameAs(context.getBean(AnalysisExecutorConfig.EXECUTOR));
	}
}
