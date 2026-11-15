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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = "app.scheduling.enabled=false")
@RecordApplicationEvents
class AnalysisRecoveryTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private AnalysisRecovery recovery;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationEvents events;

	@MockitoSpyBean
	private AnalysisService analysisService;

	@BeforeEach
	@AfterEach
	void cleanTables() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
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

		// then
		Post failed = postRepository.findById(stuck).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(failed.getFailedStage()).isEqualTo(PostStatus.ANALYZING);
		assertThat(failed.getFailureReason()).isEqualTo("알 수 없는 오류가 났습니다. 서버 로그를 확인해 주세요.");
		assertThat(events.stream(PostAnalyzedEvent.class))
				.containsExactly(new PostAnalyzedEvent(stuck, PostStatus.FAILED));
		verify(analysisService, timeout(5000)).analyze(received, PostStatus.RECEIVED);
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
}
