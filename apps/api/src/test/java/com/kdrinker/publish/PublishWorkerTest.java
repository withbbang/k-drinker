package com.kdrinker.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.instagram.InstagramApiException;
import com.kdrinker.instagram.InstagramClient;
import com.kdrinker.instagram.InstagramClient.ContainerStatus;
import com.kdrinker.instagram.service.InstagramTokenService;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostPublishFinishedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = "app.scheduling.enabled=false")
@RecordApplicationEvents
class PublishWorkerTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);
	private static final String TOKEN = "tok";
	private static final String CAPTION_STORED = "본문\n<!--brewery-->\n양조장\n<!--brewery-->\n#태그";

	@Autowired
	private PublishWorker worker;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostPhotoRepository photoRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationEvents events;

	@MockitoBean
	private InstagramClient client;

	@MockitoBean
	private InstagramTokenService tokenService;

	@MockitoBean
	private PhotoService photoService;

	@MockitoBean
	private PublishSleeper sleeper;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
		when(tokenService.currentToken()).thenReturn(TOKEN);
		when(photoService.publicUrl(any())).thenAnswer(inv -> "https://img/" + ((PostPhoto) inv.getArgument(0)).getPosition());
	}

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
	}

	private Long post(int photos, String containerId) {
		Post post = Post.receive(PostSource.TELEGRAM, null, null, CLOCK);
		ReflectionTestUtils.setField(post, "status", PostStatus.PUBLISHING);
		ReflectionTestUtils.setField(post, "version", 5);
		post.writeCaption(CAPTION_STORED, "#태그", null, null);
		if (containerId != null) {
			ReflectionTestUtils.setField(post, "igContainerId", containerId);
		}
		Post saved = postRepository.saveAndFlush(post);
		// 위치 순서와 다르게 저장해 정렬을 확인한다
		for (int i = photos - 1; i >= 0; i--) {
			photoRepository.saveAndFlush(PostPhoto.of(saved, i, "o/" + i, ("t" + saved.getId() + "p" + i + "x".repeat(43)).substring(0, 43),
					FitMode.CROP));
		}
		return saved.getId();
	}

	private Post reload(Long id) {
		return postRepository.findById(id).orElseThrow();
	}

	private List<PostPublishFinishedEvent> finished() {
		return events.stream(PostPublishFinishedEvent.class).toList();
	}

	@Test
	void 사진_한_장은_캐러셀_없이_캡션이_든_단일_컨테이너로_게시한다() {
		// given
		Long id = post(1, null);
		when(client.createImageContainer(eq(TOKEN), eq("https://img/0"), eq(false), anyString())).thenReturn("c1");
		when(client.containerStatus(TOKEN, "c1")).thenReturn(ContainerStatus.FINISHED);
		when(client.publish(TOKEN, "c1")).thenReturn("m1");
		when(client.permalink(TOKEN, "m1")).thenReturn(Optional.of("https://ig/p/1"));

		// when
		worker.publish(id);

		// then
		ArgumentCaptor<String> caption = ArgumentCaptor.forClass(String.class);
		verify(client).createImageContainer(eq(TOKEN), eq("https://img/0"), eq(false), caption.capture());
		assertThat(caption.getValue()).doesNotContain("<!--brewery-->").startsWith("본문");
		verify(client, never()).createCarouselContainer(any(), anyList(), any());
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(after.getIgMediaId()).isEqualTo("m1");
		assertThat(after.getPermalink()).isEqualTo("https://ig/p/1");
		assertThat(after.getPublishedAt()).isNotNull();
	}

	@Test
	void 캐러셀은_위치_순서를_지키고_부모_id를_게시_전에_저장한다() {
		// given
		Long id = post(3, null);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(true), isNull())).thenReturn("a", "b", "c");
		when(client.createCarouselContainer(eq(TOKEN), anyList(), anyString())).thenReturn("parent");
		when(client.containerStatus(TOKEN, "parent")).thenReturn(ContainerStatus.FINISHED);
		when(client.publish(TOKEN, "parent")).thenAnswer(inv -> {
			// publish 시점에 부모 id가 이미 DB에 커밋돼 있어야 한다
			assertThat(reload(id).getIgContainerId()).isEqualTo("parent");
			return "m1";
		});
		when(client.permalink(TOKEN, "m1")).thenReturn(Optional.empty());

		// when
		worker.publish(id);

		// then
		InOrder order = inOrder(client);
		order.verify(client).createImageContainer(TOKEN, "https://img/0", true, null);
		order.verify(client).createImageContainer(TOKEN, "https://img/1", true, null);
		order.verify(client).createImageContainer(TOKEN, "https://img/2", true, null);
		ArgumentCaptor<String> caption = ArgumentCaptor.forClass(String.class);
		order.verify(client).createCarouselContainer(eq(TOKEN), eq(List.of("a", "b", "c")), caption.capture());
		order.verify(client).publish(TOKEN, "parent");
		assertThat(caption.getValue()).doesNotContain("<!--brewery-->");
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(after.getPermalink()).isNull();
	}

	@Test
	void 저장된_컨테이너가_이미_게시됐으면_다시_올리지_않는다() {
		// given
		Long id = post(2, "old");
		when(client.containerStatus(TOKEN, "old")).thenReturn(ContainerStatus.PUBLISHED);

		// when
		worker.publish(id);

		// then
		verify(client, never()).publish(any(), any());
		verify(client, never()).createImageContainer(any(), any(), anyBoolean(), any());
		verify(client, never()).createCarouselContainer(any(), anyList(), any());
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(after.getIgMediaId()).isNull();
		assertThat(after.getPermalink()).isNull();
		assertThat(finished()).containsExactly(new PostPublishFinishedEvent(id, PostStatus.PUBLISHED));
	}

	@Test
	void 저장된_컨테이너가_완료됐으면_새로_만들지_않고_게시만_한다() {
		// given
		Long id = post(1, "old");
		when(client.containerStatus(TOKEN, "old")).thenReturn(ContainerStatus.FINISHED);
		when(client.publish(TOKEN, "old")).thenReturn("m9");
		when(client.permalink(TOKEN, "m9")).thenReturn(Optional.of("https://ig/p/9"));

		// when
		worker.publish(id);

		// then
		verify(client, never()).createImageContainer(any(), any(), anyBoolean(), any());
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(reload(id).getIgMediaId()).isEqualTo("m9");
	}

	@Test
	void 저장된_컨테이너가_처리_중이면_기다린_뒤_게시한다() {
		// given
		Long id = post(1, "old");
		when(client.containerStatus(TOKEN, "old")).thenReturn(ContainerStatus.IN_PROGRESS, ContainerStatus.FINISHED);
		when(client.publish(TOKEN, "old")).thenReturn("m9");
		when(client.permalink(TOKEN, "m9")).thenReturn(Optional.empty());

		// when
		worker.publish(id);

		// then
		verify(client, never()).createImageContainer(any(), any(), anyBoolean(), any());
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.PUBLISHED);
	}

	@Test
	void 저장된_컨테이너가_만료됐거나_조회에_실패하면_새로_만든다() {
		// given
		Long expired = post(1, "old1");
		when(client.containerStatus(TOKEN, "old1")).thenReturn(ContainerStatus.EXPIRED);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString())).thenReturn("new1");
		when(client.containerStatus(TOKEN, "new1")).thenReturn(ContainerStatus.FINISHED);
		when(client.publish(TOKEN, "new1")).thenReturn("m1");
		when(client.permalink(TOKEN, "m1")).thenReturn(Optional.empty());

		// when
		worker.publish(expired);

		// then
		assertThat(reload(expired).getIgContainerId()).isEqualTo("new1");
		assertThat(reload(expired).getStatus()).isEqualTo(PostStatus.PUBLISHED);
		verify(client, never()).publish(TOKEN, "old1");
	}

	@Test
	void 조회가_실패하면_새_컨테이너로_이어간다() {
		// given
		Long id = post(1, "old2");
		when(client.containerStatus(TOKEN, "old2")).thenThrow(new InstagramApiException(-1, null, "조회 실패"));
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString())).thenReturn("new2");
		when(client.containerStatus(TOKEN, "new2")).thenReturn(ContainerStatus.FINISHED);
		when(client.publish(TOKEN, "new2")).thenReturn("m2");
		when(client.permalink(TOKEN, "m2")).thenReturn(Optional.empty());

		// when
		worker.publish(id);

		// then
		assertThat(reload(id).getIgContainerId()).isEqualTo("new2");
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.PUBLISHED);
	}

	@Test
	void 컨테이너가_오류_상태면_이미지_가져오기_실패다() {
		// given
		Long id = post(1, null);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString())).thenReturn("c1");
		when(client.containerStatus(TOKEN, "c1")).thenReturn(ContainerStatus.ERROR);

		// when
		worker.publish(id);

		// then
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(after.getFailedStage()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(after.getFailureReason()).isEqualTo("인스타그램이 이미지를 가져가지 못했습니다.");
		assertThat(after.getIgContainerId()).isEqualTo("c1");
		verify(client, never()).publish(any(), any());
	}

	@Test
	void 인증_오류는_인증_사유로_분류한다() {
		// given
		Long id = post(1, null);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString()))
				.thenThrow(new InstagramApiException(190, null, "Invalid OAuth access token"));

		// when
		worker.publish(id);

		// then
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(after.getFailureReason()).isEqualTo("인스타그램 인증에 문제가 있습니다. 토큰을 확인해 주세요.");
	}

	@Test
	void 그_밖의_예외는_알_수_없음으로_기록한다() {
		// given
		Long id = post(1, null);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString()))
				.thenThrow(new IllegalStateException("boom"));

		// when
		worker.publish(id);

		// then
		assertThat(reload(id).getFailureReason()).isEqualTo(PublishFailure.UNKNOWN.message());
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.FAILED);
	}

	@Test
	void 컨테이너가_5분_안에_끝나지_않으면_알_수_없음_실패이고_60초_간격으로_기다린다() throws Exception {
		// given
		Long id = post(1, null);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString())).thenReturn("c1");
		when(client.containerStatus(TOKEN, "c1")).thenReturn(ContainerStatus.IN_PROGRESS);

		// when
		worker.publish(id);

		// then
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(after.getFailureReason()).isEqualTo(PublishFailure.UNKNOWN.message());
		verify(sleeper, times(5)).sleep(Duration.ofSeconds(60));
		verify(client, times(6)).containerStatus(TOKEN, "c1");
		verify(client, never()).publish(any(), any());
	}

	@Test
	void 게시_중이_아니면_아무것도_하지_않는다() {
		// given
		Long id = post(1, null);
		jdbc.update("UPDATE post SET status = 'FAILED' WHERE id = ?", id);

		// when
		worker.publish(id);

		// then
		verify(client, never()).createImageContainer(any(), any(), anyBoolean(), any());
		assertThat(finished()).isEmpty();
	}

	@Test
	void 한_번_실행에_이벤트는_정확히_한_번이다() {
		// given
		Long ok = post(1, null);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString())).thenReturn("c1");
		when(client.containerStatus(TOKEN, "c1")).thenReturn(ContainerStatus.FINISHED);
		when(client.publish(TOKEN, "c1")).thenReturn("m1");
		when(client.permalink(TOKEN, "m1")).thenReturn(Optional.empty());

		// when
		worker.publish(ok);

		// then
		assertThat(finished()).containsExactly(new PostPublishFinishedEvent(ok, PostStatus.PUBLISHED));
	}

	@Test
	void 실패해도_이벤트는_한_번이다() {
		// given
		Long id = post(1, null);
		when(client.createImageContainer(eq(TOKEN), anyString(), eq(false), anyString()))
				.thenThrow(new InstagramApiException(190, null, "x"));

		// when
		worker.publish(id);

		// then
		assertThat(finished()).containsExactly(new PostPublishFinishedEvent(id, PostStatus.FAILED));
	}
}
