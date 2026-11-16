package com.kdrinker.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostPublishFinishedEvent;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = "app.scheduling.enabled=false")
class PublishRecoveryTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private PublishRecovery recovery;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Collector collector;

	@Autowired
	@Qualifier(PublishExecutorConfig.EXECUTOR)
	private ThreadPoolTaskExecutor publishExecutor;

	@TestConfiguration
	static class CollectorConfig {

		@Bean
		Collector publishEventCollector() {
			return new Collector();
		}
	}

	static class Collector {

		final List<PostPublishFinishedEvent> events = new CopyOnWriteArrayList<>();

		@EventListener
		void on(PostPublishFinishedEvent event) {
			events.add(event);
		}
	}

	private void drain() throws Exception {
		for (int i = 0; i < 2; i++) {
			publishExecutor.submit(() -> { }).get(10, TimeUnit.SECONDS);
		}
	}

	@BeforeEach
	@AfterEach
	void clean() throws Exception {
		drain();
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
		collector.events.clear();
	}

	@Test
	void 기동하면_게시_중에_멈춘_게시물을_실패로_바꾸고_컨테이너_id는_남긴다() throws Exception {
		// given
		Post post = Post.receive(PostSource.TELEGRAM, null, null, CLOCK);
		ReflectionTestUtils.setField(post, "status", PostStatus.PUBLISHING);
		ReflectionTestUtils.setField(post, "igContainerId", "container-1");
		Long id = postRepository.saveAndFlush(post).getId();

		// when
		recovery.recover();
		drain();

		// then
		Post after = postRepository.findById(id).orElseThrow();
		assertThat(after.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(after.getFailedStage()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(after.getFailureReason()).isEqualTo(PublishFailure.UNKNOWN.message());
		assertThat(after.getIgContainerId()).isEqualTo("container-1");
		assertThat(collector.events).containsExactly(new PostPublishFinishedEvent(id, PostStatus.FAILED));
	}
}
