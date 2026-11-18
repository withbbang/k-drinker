package com.kdrinker.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.AnalysisService;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.post.dto.PostDetailResponse;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.publish.ConfirmResult;
import com.kdrinker.publish.PublishService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class PostActionServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC);
	private static final long ID = 7L;

	private PostRepository postRepository;
	private PublishService publishService;
	private AnalysisService analysisService;
	private PostQueryService postQueryService;
	private List<Runnable> submitted;
	private PostActionService service;
	private PostDetailResponse detail;

	@BeforeEach
	void setUp() {
		postRepository = mock(PostRepository.class);
		publishService = mock(PublishService.class);
		analysisService = mock(AnalysisService.class);
		postQueryService = mock(PostQueryService.class);
		submitted = new ArrayList<>();
		Executor executor = submitted::add;
		service = new PostActionService(postRepository, publishService, analysisService, executor, postQueryService,
				CLOCK, mock(PlatformTransactionManager.class));
		detail = new PostDetailResponse(ID, "PUBLISHING", 3, false, null, null, null, null, null, null, null, "", "",
				"", false, 0, List.of(), null, "WEB");
		when(postQueryService.detail(ID)).thenReturn(detail);
	}

	private Post post(PostSource source, PostStatus status, int version, PostStatus failedStage) {
		Post post = Post.receive(source, null, null, CLOCK);
		ReflectionTestUtils.setField(post, "id", ID);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "version", version);
		ReflectionTestUtils.setField(post, "failedStage", failedStage);
		when(postRepository.findById(ID)).thenReturn(Optional.of(post));
		return post;
	}

	private static void assertCode(Runnable call, ErrorCode code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(code));
	}

	@Test
	void 웹_담당_검토_대기_게시물을_확인하면_게시를_시작하고_상세를_돌려준다() {
		// given
		post(PostSource.SHARE, PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(ID, 2)).thenReturn(ConfirmResult.STARTED);

		// when
		PostDetailResponse result = service.confirmFromWeb(ID, 2);

		// then
		verify(publishService, times(1)).confirm(ID, 2);
		assertThat(result).isSameAs(detail);
	}

	@Test
	void 없는_게시물은_COMMON_NOT_FOUND다() {
		// given
		when(postRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertCode(() -> service.confirmFromWeb(ID, 2), ErrorCode.COMMON_NOT_FOUND);
		verifyNoInteractions(publishService);
	}

	@Test
	void 텔레그램_담당_게시물을_웹에서_확인하면_POST_NOT_EDITABLE이다() {
		// given
		post(PostSource.TELEGRAM, PostStatus.PENDING_REVIEW, 2, null);

		// when / then
		assertCode(() -> service.confirmFromWeb(ID, 2), ErrorCode.POST_NOT_EDITABLE);
		assertCode(() -> service.cancelFromWeb(ID, 2), ErrorCode.POST_NOT_EDITABLE);
		assertCode(() -> service.retryFromWeb(ID, 2), ErrorCode.POST_NOT_EDITABLE);
		verifyNoInteractions(publishService, analysisService);
	}

	@Test
	void 확인_결과가_STALE이면_POST_VERSION_CONFLICT다() {
		// given
		post(PostSource.SHARE, PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(ID, 2)).thenReturn(ConfirmResult.STALE);

		// when / then
		assertCode(() -> service.confirmFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
	}

	@Test
	void 확인_결과가_CAPTION_TOO_LONG이면_POST_CAPTION_TOO_LONG이다() {
		// given
		post(PostSource.SHARE, PostStatus.PENDING_REVIEW, 2, null);
		when(publishService.confirm(ID, 2)).thenReturn(ConfirmResult.CAPTION_TOO_LONG);

		// when / then
		assertCode(() -> service.confirmFromWeb(ID, 2), ErrorCode.POST_CAPTION_TOO_LONG);
	}

	@Test
	void 검토_대기가_아닌_게시물을_확인하면_POST_VERSION_CONFLICT다() {
		// given
		post(PostSource.SHARE, PostStatus.PUBLISHED, 2, null);

		// when / then
		assertCode(() -> service.confirmFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
		verifyNoInteractions(publishService);
	}

	@Test
	void 버전이_다르면_취소하지_않고_POST_VERSION_CONFLICT다() {
		// given
		post(PostSource.SHARE, PostStatus.PENDING_REVIEW, 3, null);

		// when / then
		assertCode(() -> service.cancelFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
		verify(postRepository, never()).transition(anyLong(), any(), any(), anyInt(), any(Instant.class));
	}

	@Test
	void 웹_담당_검토_대기_게시물을_취소하면_조건부_전이_후_상세를_돌려준다() {
		// given
		post(PostSource.SHARE, PostStatus.PENDING_REVIEW, 2, null);
		when(postRepository.transition(ID, PostStatus.PENDING_REVIEW, PostStatus.CANCELED, 2, CLOCK.instant()))
			.thenReturn(1);

		// when
		PostDetailResponse result = service.cancelFromWeb(ID, 2);

		// then
		assertThat(result).isSameAs(detail);
	}

	@Test
	void 취소_전이가_0행이면_POST_VERSION_CONFLICT다() {
		// given
		post(PostSource.SHARE, PostStatus.PENDING_REVIEW, 2, null);
		when(postRepository.transition(eq(ID), any(), any(), eq(2), any(Instant.class))).thenReturn(0);

		// when / then
		assertCode(() -> service.cancelFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
	}

	@Test
	void 분석_단계_실패를_재시도하면_응답_전에_분석_중으로_바꾸고_실행기에는_이어서_분석을_넣는다() {
		// given
		post(PostSource.SHARE, PostStatus.FAILED, 2, PostStatus.ANALYZING);
		when(analysisService.startRetry(ID, 2)).thenReturn(true);

		// when
		PostDetailResponse result = service.retryFromWeb(ID, 2);

		// then
		verify(analysisService).startRetry(ID, 2);
		assertThat(submitted).hasSize(1);
		verify(analysisService, never()).resume(anyLong());
		submitted.get(0).run();
		verify(analysisService).resume(ID);
		verify(analysisService, never()).analyze(anyLong(), any());
		verifyNoInteractions(publishService);
		assertThat(result).isSameAs(detail);
	}

	@Test
	void 분석_재시도_전이를_이미_다른_요청이_가져갔으면_POST_VERSION_CONFLICT이고_아무것도_넣지_않는다() {
		// given
		post(PostSource.SHARE, PostStatus.FAILED, 2, PostStatus.ANALYZING);
		when(analysisService.startRetry(ID, 2)).thenReturn(false);

		// when / then
		assertCode(() -> service.retryFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
		assertThat(submitted).isEmpty();
	}

	@Test
	void 게시_재시도_결과가_CAPTION_TOO_LONG이면_POST_CAPTION_TOO_LONG이다() {
		// given
		post(PostSource.SHARE, PostStatus.FAILED, 2, PostStatus.PUBLISHING);
		when(publishService.retry(ID, 2)).thenReturn(ConfirmResult.CAPTION_TOO_LONG);

		// when / then
		assertCode(() -> service.retryFromWeb(ID, 2), ErrorCode.POST_CAPTION_TOO_LONG);
	}

	@Test
	void 게시_중인_게시물을_확인하면_POST_VERSION_CONFLICT다() {
		// given
		post(PostSource.SHARE, PostStatus.PUBLISHING, 2, null);

		// when / then
		assertCode(() -> service.confirmFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
		verifyNoInteractions(publishService);
	}

	@Test
	void 게시_단계_실패를_재시도하면_게시_재시도를_부른다() {
		// given
		post(PostSource.SHARE, PostStatus.FAILED, 2, PostStatus.PUBLISHING);
		when(publishService.retry(ID, 2)).thenReturn(ConfirmResult.STARTED);

		// when
		PostDetailResponse result = service.retryFromWeb(ID, 2);

		// then
		verify(publishService).retry(ID, 2);
		assertThat(submitted).isEmpty();
		assertThat(result).isSameAs(detail);
	}

	@Test
	void 게시_재시도_결과가_STALE이면_POST_VERSION_CONFLICT다() {
		// given
		post(PostSource.SHARE, PostStatus.FAILED, 2, PostStatus.PUBLISHING);
		when(publishService.retry(ID, 2)).thenReturn(ConfirmResult.STALE);

		// when / then
		assertCode(() -> service.retryFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
	}

	@Test
	void 실패가_아닌_게시물을_재시도하면_POST_VERSION_CONFLICT다() {
		// given
		post(PostSource.SHARE, PostStatus.PENDING_REVIEW, 2, null);

		// when / then
		assertCode(() -> service.retryFromWeb(ID, 2), ErrorCode.POST_VERSION_CONFLICT);
		assertThat(submitted).isEmpty();
		verifyNoInteractions(publishService);
	}

	@Test
	void retry는_버전이_다르면_STALE이고_아무것도_넣지_않는다() {
		// given
		post(PostSource.TELEGRAM, PostStatus.FAILED, 3, PostStatus.ANALYZING);

		// when
		ConfirmResult result = service.retry(ID, 2);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STALE);
		assertThat(submitted).isEmpty();
		verifyNoInteractions(analysisService);
	}

}
