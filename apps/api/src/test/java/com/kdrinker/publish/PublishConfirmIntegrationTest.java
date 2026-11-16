package com.kdrinker.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/** 실제 DB로 [확인]은 저장된 컨테이너를 비우고 [재시도]는 남기는지 확인한다. 게시 작업(PublishWorker)은 목이다. */
@SpringBootTest(properties = "app.scheduling.enabled=false")
class PublishConfirmIntegrationTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private PublishService publishService;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private PublishWorker worker;

	@BeforeEach
	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
	}

	private Long save(PostStatus status, PostStatus failedStage) {
		Post post = Post.receive(PostSource.TELEGRAM, null, null, CLOCK);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "failedStage", failedStage);
		ReflectionTestUtils.setField(post, "igContainerId", "container-1");
		return postRepository.saveAndFlush(post).getId();
	}

	@Test
	void 확인하면_저장된_컨테이너_id를_비운다() {
		// given
		Long id = save(PostStatus.PENDING_REVIEW, null);
		int version = postRepository.findById(id).orElseThrow().getVersion();

		// when
		ConfirmResult result = publishService.confirm(id, version);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STARTED);
		Post after = postRepository.findById(id).orElseThrow();
		assertThat(after.getStatus()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(after.getIgContainerId()).isNull();
	}

	@Test
	void 재시도하면_저장된_컨테이너_id를_남긴다() {
		// given
		Long id = save(PostStatus.FAILED, PostStatus.PUBLISHING);
		int version = postRepository.findById(id).orElseThrow().getVersion();

		// when
		ConfirmResult result = publishService.retry(id, version);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STARTED);
		assertThat(postRepository.findById(id).orElseThrow().getIgContainerId()).isEqualTo("container-1");
	}

}
