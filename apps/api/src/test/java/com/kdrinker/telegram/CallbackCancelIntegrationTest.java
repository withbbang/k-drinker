package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.telegram.dto.CallbackQuery;
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

/** 실제 DB로 [취소]가 트랜잭션 안에서 PENDING_REVIEW → CANCELED 로 바꾸는지 확인한다. 텔레그램 HTTP만 목이다. */
@SpringBootTest(properties = "app.scheduling.enabled=false")
class CallbackCancelIntegrationTest {

	private static final long OWNER = 100L;
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private CallbackHandler handler;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private TelegramClient client;

	@MockitoBean
	private PostNotifier notifier;

	@BeforeEach
	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
	}

	@Test
	void 취소_버튼은_실제_DB에서_검토_대기_게시물을_취소로_바꾼다() {
		// given
		when(client.isOwnerChat(OWNER)).thenReturn(true);
		Post post = Post.receive(PostSource.TELEGRAM, null, null, CLOCK);
		org.springframework.test.util.ReflectionTestUtils.setField(post, "status", PostStatus.PENDING_REVIEW);
		post = postRepository.saveAndFlush(post);
		Long id = post.getId();
		int version = postRepository.findById(id).orElseThrow().getVersion();

		// when
		handler.handle(new CallbackQuery("cb1", OWNER, 77L, "cancel:" + id + ":" + version));

		// then
		assertThat(postRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PostStatus.CANCELED);
		verify(notifier).notifyCanceled(id);
		verify(client).removeKeyboard(77L);
	}

}
