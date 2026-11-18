package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostChannel;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.publish.CaptionLengthGuard;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostNotifierTest {

	private final TelegramSender sender = mock(TelegramSender.class);
	private final TelegramClient client = mock(TelegramClient.class);
	private final PostRepository postRepository = mock(PostRepository.class);
	private final PostPhotoRepository photoRepository = mock(PostPhotoRepository.class);
	private final CaptionLengthGuard guard = mock(CaptionLengthGuard.class);
	private PostNotifier notifier;

	@BeforeEach
	void setUp() {
		TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
		notifier = new PostNotifier(sender, client, postRepository, photoRepository, guard, tx);
		when(photoRepository.findByPostIdOrderByPositionAsc(anyLong())).thenReturn(List.of());
		when(sender.send(any(), any(), any(), any())).thenReturn(Optional.of(77L));
	}

	private Post post(PostStatus status, int version) {
		Post post = Post.receive(PostSource.TELEGRAM, null, new BigDecimal("4.0"), Clock.systemUTC());
		ReflectionTestUtils.setField(post, "id", 5L);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "version", version);
		when(postRepository.findById(5L)).thenReturn(Optional.of(post));
		return post;
	}

	@Test
	void receivedSentOncePerPost() {
		when(photoRepository.findByPostIdOrderByPositionAsc(5L)).thenReturn(List.of());
		post(PostStatus.RECEIVED, 0);

		notifier.notifyReceived(5L);
		notifier.notifyReceived(5L);

		verify(sender, times(2)).send(eq("RECEIVED:5"), eq(NotificationKind.RECEIVED), eq(5L), any(TelegramText.class));
	}

	@Test
	void newOverviewRemovesOldButtons() {
		post(PostStatus.PENDING_REVIEW, 3);
		when(sender.messageIds(5L, NotificationKind.OVERVIEW)).thenReturn(List.of(70L, 77L));

		notifier.notifyReviewReady(5L);

		verify(sender).send(eq("OVERVIEW:5:3"), eq(NotificationKind.OVERVIEW), eq(5L), any(TelegramText.class));
		verify(client).removeKeyboard(70L);
		verify(client, never()).removeKeyboard(77L);
	}

	@Test
	void tooLongCaptionSkipsOverview() {
		post(PostStatus.PENDING_REVIEW, 3);
		when(guard.failIfTooLong(5L)).thenReturn(true);

		notifier.notifyReviewReady(5L);

		verify(sender, never()).send(any(), any(), any(), any());
	}

	@Test
	void notPendingSkipsOverview() {
		post(PostStatus.CANCELED, 4);

		notifier.notifyReviewReady(5L);

		verify(sender, never()).send(any(), any(), any(), any());
	}

	@Test
	void analysisFailedUsesVersionKey() {
		post(PostStatus.FAILED, 3);

		notifier.notifyAnalysisFailed(5L);

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(sender).send(key.capture(), eq(NotificationKind.ANALYSIS_FAILED), eq(5L), any(TelegramText.class));
		assertThat(key.getValue()).isEqualTo("ANALYSIS_FAILED:5:3");
	}

	@Test
	void publishFinishedPicksKindByStatus() {
		post(PostStatus.PUBLISHED, 6);

		notifier.notifyPublishFinished(5L);

		verify(sender).send(eq("PUBLISHED:5"), eq(NotificationKind.PUBLISHED), eq(5L), any(TelegramText.class));
	}

	@Test
	void 웹_담당_게시물에는_어떤_알림도_보내지_않는다() {
		// given
		Post post = post(PostStatus.PENDING_REVIEW, 3);
		post.moveToWeb();

		// when
		notifier.notifyReceived(5L);
		notifier.notifyReviewReady(5L);
		notifier.notifyAnalysisFailed(5L);
		notifier.notifyPublishing(5L);
		notifier.notifyPublishFinished(5L);
		notifier.notifyCanceled(5L);

		// then
		verify(sender, never()).send(any(), any(), any(), any());
		verify(client, never()).removeKeyboard(anyLong());
	}

	@Test
	void 웹_담당_게시물은_게시_완료여도_알림을_보내지_않는다() {
		// given
		Post post = post(PostStatus.PUBLISHED, 6);
		post.moveToWeb();

		// when
		notifier.notifyPublishFinished(5L);

		// then
		verify(sender, never()).send(any(), any(), any(), any());
	}

	@Test
	void 웹_담당_게시물은_게시_실패여도_알림을_보내지_않는다() {
		// given
		Post post = post(PostStatus.FAILED, 6);
		post.moveToWeb();

		// when
		notifier.notifyPublishFinished(5L);

		// then
		verify(sender, never()).send(any(), any(), any(), any());
	}

	@Test
	void 웹_담당_게시물이라도_캡션_길이_검사는_먼저_한다() {
		// given
		Post post = post(PostStatus.PENDING_REVIEW, 3);
		post.moveToWeb();

		// when
		notifier.notifyReviewReady(5L);

		// then
		verify(guard).failIfTooLong(5L);
		verify(sender, never()).send(any(), any(), any(), any());
	}

	@Test
	void 개요_버튼_제거는_하나가_실패해도_나머지를_계속_지운다() {
		// given
		when(sender.messageIds(5L, NotificationKind.OVERVIEW)).thenReturn(List.of(70L, 71L, 72L));
		doThrow(new RuntimeException("telegram down")).when(client).removeKeyboard(71L);

		// when
		notifier.removeOverviewButtons(5L);

		// then
		verify(client).removeKeyboard(70L);
		verify(client).removeKeyboard(71L);
		verify(client).removeKeyboard(72L);
		verify(sender, never()).send(any(), any(), any(), any());
	}

	@Test
	void 웹으로_넘어가면_실패_알림의_버튼도_지운다() {
		// given
		when(sender.messageIds(5L, NotificationKind.OVERVIEW)).thenReturn(List.of(70L));
		when(sender.messageIds(5L, NotificationKind.ANALYSIS_FAILED)).thenReturn(List.of(80L));
		when(sender.messageIds(5L, NotificationKind.PUBLISH_FAILED)).thenReturn(List.of(90L, 91L));

		// when
		notifier.removeOverviewButtons(5L);

		// then
		verify(client).removeKeyboard(70L);
		verify(client).removeKeyboard(80L);
		verify(client).removeKeyboard(90L);
		verify(client).removeKeyboard(91L);
	}

}
