package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = "app.scheduling.enabled=false")
class NotificationReconcilerTest {

	@Autowired
	private NotificationReconciler reconciler;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock clock;

	@Autowired
	@Qualifier(TelegramExecutorConfig.EXECUTOR)
	private ThreadPoolTaskExecutor telegramExecutor;

	@Autowired
	@Qualifier("publishExecutor")
	private ThreadPoolTaskExecutor publishExecutor;

	@MockitoBean
	private TelegramClient client;

	private final AtomicLong messageIds = new AtomicLong(100);

	private void drain() throws Exception {
		for (int i = 0; i < 2; i++) {
			publishExecutor.submit(() -> { }).get(10, TimeUnit.SECONDS);
			telegramExecutor.submit(() -> { }).get(10, TimeUnit.SECONDS);
		}
	}

	private void clean() throws Exception {
		drain();
		jdbc.update("DELETE FROM telegram_notification");
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
	}

	@BeforeEach
	void setUp() throws Exception {
		clean();
		when(client.isConfigured()).thenReturn(true);
		when(client.sendMessage(any())).thenAnswer(inv -> messageIds.incrementAndGet());
		clearInvocations(client);
	}

	@AfterEach
	void tearDown() throws Exception {
		clean();
	}

	private Long post(PostStatus status, PostStatus failedStage, Instant updatedAt) {
		Post post = Post.receive(PostSource.TELEGRAM, null, null, clock);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "failedStage", failedStage);
		ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
		Long id = postRepository.saveAndFlush(post).getId();
		// 엔티티 리스너가 updatedAt을 덮어쓸 수 있어 직접 맞춘다
		jdbc.update("UPDATE post SET updated_at = ? WHERE id = ?", java.sql.Timestamp.from(updatedAt), id);
		return id;
	}

	private int notificationCount() {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM telegram_notification", Integer.class);
		return n == null ? 0 : n;
	}

	@Test
	void 검토_대기와_실패_게시물의_알림을_한_번씩_보내고_두_번째_실행은_아무것도_보내지_않는다() throws Exception {
		// given
		Instant recent = clock.instant().minus(1, ChronoUnit.DAYS);
		post(PostStatus.PENDING_REVIEW, null, recent);
		post(PostStatus.FAILED, PostStatus.ANALYZING, recent);
		post(PostStatus.FAILED, PostStatus.PUBLISHING, recent);

		// when
		reconciler.reconcileNow();
		drain();

		// then
		verify(client, times(3)).sendMessage(any());
		assertThat(notificationCount()).isEqualTo(3);
		assertThat(jdbc.queryForList("SELECT dedup_key FROM telegram_notification", String.class))
			.anyMatch(k -> k.startsWith("OVERVIEW:"))
			.anyMatch(k -> k.startsWith("ANALYSIS_FAILED:"))
			.anyMatch(k -> k.startsWith("PUBLISH_FAILED:"));

		// when 두 번째 실행
		clearInvocations(client);
		reconciler.reconcileNow();
		drain();

		// then
		verify(client, never()).sendMessage(any());
		verify(client, never()).removeKeyboard(org.mockito.ArgumentMatchers.anyLong());
		assertThat(notificationCount()).isEqualTo(3);
	}

	@Test
	void 일주일보다_오래된_게시물은_건너뛴다() throws Exception {
		// given
		Instant old = clock.instant().minus(8, ChronoUnit.DAYS);
		post(PostStatus.PENDING_REVIEW, null, old);
		post(PostStatus.FAILED, PostStatus.PUBLISHING, old);

		// when
		reconciler.reconcileNow();
		drain();

		// then
		verify(client, never()).sendMessage(any());
		assertThat(notificationCount()).isZero();
	}
}
