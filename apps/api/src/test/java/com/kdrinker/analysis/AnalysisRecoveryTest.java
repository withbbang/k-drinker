package com.kdrinker.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostAnalyzedEvent;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = "app.scheduling.enabled=false")
class AnalysisRecoveryTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private AnalysisRecovery recovery;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AnalyzedEventCollector collector;

	@MockitoSpyBean
	private AnalysisService analysisService;

	/** 복구 작업은 분석 실행기 스레드에서 이벤트를 내므로(테스트 스레드 기록인 ApplicationEvents로는 못 잡는다) 직접 모은다. */
	@TestConfiguration
	static class CollectorConfig {

		@Bean
		AnalyzedEventCollector analyzedEventCollector() {
			return new AnalyzedEventCollector();
		}
	}

	static class AnalyzedEventCollector {

		final List<PostAnalyzedEvent> events = new CopyOnWriteArrayList<>();

		@EventListener
		void on(PostAnalyzedEvent event) {
			events.add(event);
		}
	}

	@Autowired
	@Qualifier(AnalysisExecutorConfig.EXECUTOR)
	private ThreadPoolTaskExecutor analysisExecutor;

	/**
	 * 앞 테스트의 복구 작업이 다음 테스트의 게시물을 건드리지 않게 분석 실행기를 비운다. 실행기는 스레드 1개 FIFO라 빈 작업을
	 * 두 번 차례로 기다리면 복구 작업과 그 작업이 넣은 게시물별 작업까지 모두 끝난다.
	 */
	private void drainExecutor() throws Exception {
		for (int i = 0; i < 2; i++) {
			analysisExecutor.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
		}
	}

	@BeforeEach
	@AfterEach
	void cleanTables() throws Exception {
		drainExecutor();
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
		collector.events.clear();
	}

	private Long post(PostStatus status) {
		Post post = Post.receive(PostSource.TELEGRAM, null, null, CLOCK);
		ReflectionTestUtils.setField(post, "status", status);
		return postRepository.saveAndFlush(post).getId();
	}

	@Test
	void 기동하면_분석_중이던_게시물은_실패로_접수된_게시물은_분석_실행기로_넘긴다() {
		// given
		doNothing().when(analysisService).analyze(any(), any());
		Long stuck = post(PostStatus.ANALYZING);
		Long received = post(PostStatus.RECEIVED);

		// when
		recovery.recover();

		// then (복구는 분석 실행기에서 돈다. RECEIVED 재제출이 실패 처리 뒤에 오므로 그것을 기다린다)
		verify(analysisService, timeout(5000)).analyze(received, PostStatus.RECEIVED);
		Post failed = postRepository.findById(stuck).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(failed.getFailedStage()).isEqualTo(PostStatus.ANALYZING);
		assertThat(failed.getFailureReason()).isEqualTo("알 수 없는 오류가 났습니다. 서버 로그를 확인해 주세요.");
		assertThat(collector.events).containsExactly(new PostAnalyzedEvent(stuck, PostStatus.FAILED));
		verify(analysisService, never()).analyze(eq(stuck), any());
	}

	@Test
	void 접수된_게시물은_기동_스레드가_아닌_분석_실행기에서_돈다() throws Exception {
		// given
		java.util.concurrent.CompletableFuture<String> ranOn = new java.util.concurrent.CompletableFuture<>();
		org.mockito.Mockito.doAnswer(inv -> {
			ranOn.complete(Thread.currentThread().getName());
			return null;
		}).when(analysisService).analyze(any(), any());
		Long received = post(PostStatus.RECEIVED);

		// when
		recovery.recover();

		// then
		verify(analysisService, timeout(5000)).analyze(received, PostStatus.RECEIVED);
		assertThat(ranOn.get(5, java.util.concurrent.TimeUnit.SECONDS)).startsWith("analysis-");
	}

	@Test
	void 멈춘_게시물_실패_처리도_분석_실행기에서_돈다() throws Exception {
		// given
		java.util.concurrent.CompletableFuture<String> ranOn = new java.util.concurrent.CompletableFuture<>();
		org.mockito.Mockito.doAnswer(inv -> {
			ranOn.complete(Thread.currentThread().getName());
			return inv.callRealMethod();
		}).when(analysisService).recordFailure(any(), any());
		Long stuck = post(PostStatus.ANALYZING);

		// when
		recovery.recover();

		// then
		assertThat(ranOn.get(5, java.util.concurrent.TimeUnit.SECONDS)).startsWith("analysis-");
		verify(analysisService, timeout(5000)).recordFailure(stuck, AnalysisFailure.UNKNOWN);
	}
}
