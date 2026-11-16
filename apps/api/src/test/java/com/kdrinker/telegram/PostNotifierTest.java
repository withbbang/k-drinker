package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.post.entity.Post;
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

}
