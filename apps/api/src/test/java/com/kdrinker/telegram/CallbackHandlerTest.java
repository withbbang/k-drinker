package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.AnalysisService;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.post.service.PostActionService;
import com.kdrinker.post.service.PostQueryService;
import com.kdrinker.publish.ConfirmResult;
import com.kdrinker.publish.PublishService;
import com.kdrinker.telegram.dto.CallbackQuery;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class CallbackHandlerTest {

	private static final long OWNER = 100L;
	private static final String STALE = "이미 처리했거나 오래된 버튼입니다. 최신 개요 메시지를 확인해 주세요.";
	private static final Clock CLOCK = Clock.systemUTC();

	private TelegramClient client;
	private TelegramSender sender;
	private PostRepository postRepository;
	private PostPhotoRepository photoRepository;
	private PublishService publishService;
	private AnalysisService analysisService;
	private PostNotifier notifier;
	private PhotoStorage photoStorage;
	private CallbackHandler handler;

	@BeforeEach
	void setUp() {
		client = mock(TelegramClient.class);
		sender = mock(TelegramSender.class);
		postRepository = mock(PostRepository.class);
		photoRepository = mock(PostPhotoRepository.class);
		publishService = mock(PublishService.class);
		analysisService = mock(AnalysisService.class);
		notifier = mock(PostNotifier.class);
		photoStorage = mock(PhotoStorage.class);
		when(client.isOwnerChat(OWNER)).thenReturn(true);
		// cancel·retry는 실제 PostActionService를 거친다(저장소·게시·분석은 목).
		PostActionService actions = new PostActionService(postRepository, publishService, analysisService,
				Runnable::run, mock(PostQueryService.class), CLOCK,
				mock(org.springframework.transaction.PlatformTransactionManager.class));
		handler = new CallbackHandler(client, sender, postRepository, photoRepository, publishService, actions,
				notifier, photoStorage, "https://k.example/");
	}

	private Post post(PostStatus status, int version, PostStatus failedStage) {
		Post post = Post.receive(PostSource.TELEGRAM, null, null, CLOCK);
		ReflectionTestUtils.setField(post, "id", 128L);
		ReflectionTestUtils.setField(post, "version", version);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "failedStage", failedStage);
		ReflectionTestUtils.setField(post, "caption", "캡션");
		when(postRepository.findById(128L)).thenReturn(Optional.of(post));
		return post;
	}

	private static CallbackQuery query(String data) {
		return new CallbackQuery("cb1", OWNER, 77L, data);
	}

	@Test
	void ignoresCallbackFromOtherChat() {
		handler.handle(new CallbackQuery("cb1", 999L, 77L, "ok:128:2"));

		verify(client).isOwnerChat(999L);
		verifyNoMoreInteractions(client);
		verifyNoInteractions(publishService, postRepository, sender, notifier);
	}

	@Test
	void staleVersionShowsPopup() {
		post(PostStatus.PENDING_REVIEW, 3, null);

		handler.handle(query("ok:128:2"));

		verify(client).answerCallback("cb1", STALE);
		verify(client, never()).removeKeyboard(anyLong());
		verifyNoInteractions(publishService);
	}

	@Test
	void unparsableMissingAndWrongStateShowStale() {
		handler.handle(query("garbage"));
		when(postRepository.findById(128L)).thenReturn(Optional.empty());
		handler.handle(query("ok:128:2"));
		post(PostStatus.PUBLISHED, 2, null);
		handler.handle(query("ok:128:2"));

		verify(client, times(3)).answerCallback("cb1", STALE);
	}

	@Test
	void okStartsPublishAndRemovesButtons() {
		post(PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(128L, 2)).thenReturn(ConfirmResult.STARTED);

		handler.handle(query("ok:128:2"));

		verify(notifier).notifyPublishing(128L);
		verify(client).answerCallback("cb1", "게시를 시작합니다.");
		verify(client).removeKeyboard(77L);
	}

	@Test
	void okStaleDoesNotRemoveButtons() {
		post(PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(128L, 2)).thenReturn(ConfirmResult.STALE);

		handler.handle(query("ok:128:2"));

		verify(client).answerCallback("cb1", STALE);
		verify(client, never()).removeKeyboard(anyLong());
		verify(notifier, never()).notifyPublishing(anyLong());
	}

	@Test
	void okCaptionTooLongRemovesButtonsWithoutNotifying() {
		post(PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(128L, 2)).thenReturn(ConfirmResult.CAPTION_TOO_LONG);

		handler.handle(query("ok:128:2"));

		verify(client).answerCallback("cb1", null);
		verify(client).removeKeyboard(77L);
		verify(notifier, never()).notifyPublishing(anyLong());
	}

	@Test
	void cancelTransitionsAndNotifies() {
		post(PostStatus.PENDING_REVIEW, 2, null);
		when(postRepository.transition(eq(128L), eq(PostStatus.PENDING_REVIEW), eq(PostStatus.CANCELED), eq(2),
				any(Instant.class))).thenReturn(1);

		handler.handle(query("cancel:128:2"));

		verify(notifier).notifyCanceled(128L);
		verify(client).answerCallback("cb1", "취소했습니다.");
		verify(client).removeKeyboard(77L);
	}

	@Test
	void cancelLostRaceShowsStale() {
		post(PostStatus.PENDING_REVIEW, 2, null);
		when(postRepository.transition(eq(128L), any(), any(), eq(2), any(Instant.class))).thenReturn(0);

		handler.handle(query("cancel:128:2"));

		verify(client).answerCallback("cb1", STALE);
		verify(notifier, never()).notifyCanceled(anyLong());
	}

	@Test
	void detailSendsPhotosInOrderWithoutStateChange() {
		Post post = post(PostStatus.PENDING_REVIEW, 2, null);
		PostPhoto first = PostPhoto.of(post, 0, "o0", "t0", FitMode.CROP);
		first.attachRendered("r0");
		PostPhoto second = PostPhoto.of(post, 1, "o1", "t1", FitMode.CROP);
		second.attachRendered("r1");
		when(photoRepository.findByPostIdOrderByPositionAsc(128L)).thenReturn(List.of(first, second));
		when(photoStorage.locate("r0")).thenReturn(Path.of("/m/r0"));
		when(photoStorage.locate("r1")).thenReturn(Path.of("/m/r1"));

		handler.handle(query("detail:128:2"));

		InOrder order = inOrder(client, sender);
		order.verify(client).sendPhotos(List.of(Path.of("/m/r0"), Path.of("/m/r1")));
		ArgumentCaptor<TelegramText> text = ArgumentCaptor.forClass(TelegramText.class);
		order.verify(sender).send(isNull(), eq(NotificationKind.DETAIL), eq(128L), text.capture());
		assertThat(text.getValue().html()).startsWith("캡션 원문 (2자)");
		verify(client).answerCallback("cb1", null);
		verify(client, never()).removeKeyboard(anyLong());
		verify(postRepository, never()).transition(anyLong(), any(), any(), any(Instant.class));
		verify(postRepository, never()).transition(anyLong(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
				any(Instant.class));
	}

	@Test
	void editSendsLinkForFailedPost() {
		post(PostStatus.FAILED, 2, PostStatus.PUBLISHING);

		handler.handle(query("edit:128:2"));

		ArgumentCaptor<TelegramText> text = ArgumentCaptor.forClass(TelegramText.class);
		verify(sender).send(isNull(), eq(NotificationKind.EDIT_LINK), eq(128L), text.capture());
		assertThat(text.getValue().html()).contains("https://k.example/posts/128/edit");
		verify(client).answerCallback("cb1", null);
		verify(client, never()).removeKeyboard(anyLong());
	}

	@Test
	void retryAnalysisSubmitsAnalyze() {
		post(PostStatus.FAILED, 2, PostStatus.ANALYZING);
		when(analysisService.startRetry(128L, 2)).thenReturn(true);

		handler.handle(query("retry:128:2"));

		verify(analysisService).startRetry(128L, 2);
		verify(analysisService).resume(128L);
		verify(notifier, never()).notifyPublishing(anyLong());
		verifyNoInteractions(publishService);
		verify(client).answerCallback("cb1", "다시 시도합니다.");
		verify(client).removeKeyboard(77L);
	}

	@Test
	void retryPublishCallsRetry() {
		post(PostStatus.FAILED, 2, PostStatus.PUBLISHING);
		when(publishService.retry(128L, 2)).thenReturn(ConfirmResult.STARTED);

		handler.handle(query("retry:128:2"));

		verify(notifier).notifyPublishing(128L);
		verify(client).answerCallback("cb1", "다시 시도합니다.");
		verify(client).removeKeyboard(77L);
	}

	@Test
	void answersExactlyOnceEvenWhenDownstreamThrows() {
		post(PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(128L, 2)).thenThrow(new IllegalStateException("boom"));

		handler.handle(query("ok:128:2"));

		verify(client, times(1)).answerCallback("cb1", null);
	}

	@Test
	void answersOnceWhenAnswerOrRemoveThrows() {
		post(PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(128L, 2)).thenReturn(ConfirmResult.STARTED);
		doThrow(new RuntimeException("net")).when(client).answerCallback(any(), any());
		doThrow(new RuntimeException("net")).when(client).removeKeyboard(anyLong());

		handler.handle(query("ok:128:2"));

		verify(client, times(1)).answerCallback(any(), any());
	}

	@Test
	void answersExactlyOnceOnEveryPath() {
		for (String act : List.of("ok", "cancel", "detail", "edit", "retry")) {
			for (PostStatus status : PostStatus.values()) {
				org.mockito.Mockito.clearInvocations(client);
				post(status, 2, PostStatus.ANALYZING);
				when(publishService.confirm(128L, 2)).thenReturn(ConfirmResult.STARTED);
				handler.handle(query(act + ":128:2"));
				verify(client, times(1)).answerCallback(eq("cb1"), any());
			}
		}
		org.mockito.Mockito.clearInvocations(client);
		handler.handle(query("nope"));
		verify(client, times(1)).answerCallback(eq("cb1"), any());
	}

	@Test
	void 웹_담당_게시물의_버튼은_모두_오래된_버튼으로_답한다() {
		// given
		post(PostStatus.PENDING_REVIEW, 2, null).moveToWeb();

		// when
		for (String act : List.of("ok", "cancel", "detail", "edit")) {
			handler.handle(query(act + ":128:2"));
		}
		post(PostStatus.FAILED, 2, PostStatus.PUBLISHING).moveToWeb();
		handler.handle(query("retry:128:2"));

		// then
		verify(client, times(5)).answerCallback("cb1", STALE);
		verify(client, never()).removeKeyboard(anyLong());
		verifyNoInteractions(publishService, analysisService, sender, notifier);
	}

	@Test
	void 분석_재시도를_다른_곳에서_먼저_시작했으면_오래된_버튼으로_답한다() {
		// given
		post(PostStatus.FAILED, 2, PostStatus.ANALYZING);
		when(analysisService.startRetry(128L, 2)).thenReturn(false);

		// when
		handler.handle(query("retry:128:2"));

		// then
		verify(client).answerCallback("cb1", STALE);
		verify(client, never()).removeKeyboard(anyLong());
		verify(analysisService, never()).resume(anyLong());
	}

}
