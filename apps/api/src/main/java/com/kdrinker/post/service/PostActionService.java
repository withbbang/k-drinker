package com.kdrinker.post.service;

import com.kdrinker.analysis.AnalysisExecutorConfig;
import com.kdrinker.analysis.AnalysisService;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.post.dto.PostDetailResponse;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostChannel;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.publish.ConfirmResult;
import com.kdrinker.publish.PublishService;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시물 확인·취소·재시도. 텔레그램 콜백({@code cancel}·{@code retry})과 웹 API({@code *FromWeb})가 같은 로직을 쓴다.
 * <p>
 * 클래스·메서드에 {@code @Transactional}을 두지 않는다. {@link PublishService}가 자기 트랜잭션을 열고, 재분석은 분석
 * 실행기에서 트랜잭션 밖으로 돌아야 한다. 바깥에 읽기 전용 트랜잭션이 있으면 그 안에 합류해 쓰기가 막힌다.
 * 상태 변경은 모두 버전 검사 조건부 전이로 하므로, 사전 확인과 전이 사이의 경합은 전이가 막는다.
 */
@Service
public class PostActionService {

	private final PostRepository postRepository;
	private final PublishService publishService;
	private final AnalysisService analysisService;
	private final Executor analysisExecutor;
	private final PostQueryService postQueryService;
	private final Clock clock;
	private final TransactionTemplate tx;
	private final TransactionTemplate readTx;

	public PostActionService(PostRepository postRepository, PublishService publishService,
			AnalysisService analysisService, @Qualifier(AnalysisExecutorConfig.EXECUTOR) Executor analysisExecutor,
			PostQueryService postQueryService, Clock clock, PlatformTransactionManager transactionManager) {
		this.postRepository = postRepository;
		this.publishService = publishService;
		this.analysisService = analysisService;
		this.analysisExecutor = analysisExecutor;
		this.postQueryService = postQueryService;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
		this.readTx = new TransactionTemplate(transactionManager);
		this.readTx.setReadOnly(true);
	}

	/** PENDING_REVIEW(버전 일치) → CANCELED 조건부 전이. 0행이면 STALE. */
	public ConfirmResult cancel(Long postId, int version) {
		Integer updated = tx.execute(status -> postRepository.transition(postId, PostStatus.PENDING_REVIEW,
				PostStatus.CANCELED, version, clock.instant()));
		return updated == null || updated == 0 ? ConfirmResult.STALE : ConfirmResult.STARTED;
	}

	/**
	 * 실패 단계가 분석이면 ANALYZING으로 바꾼 뒤 분석 실행기에 이어서 분석을 넣고, 게시면 {@link PublishService#retry}를 부른다.
	 * 실패 상태가 아니거나 버전이 다르면 STALE.
	 */
	public ConfirmResult retry(Long postId, int version) {
		State state = readState(postId);
		if (state == null || state.status() != PostStatus.FAILED || state.version() != version) {
			return ConfirmResult.STALE;
		}
		if (state.failedStage() == PostStatus.ANALYZING) {
			// FAILED → ANALYZING 버전 검사 전이는 여기서 바로 한다. 응답이 ANALYZING을 보여 주고, 두 번 눌러도 한 번만 넣는다.
			// 분석 본체(Claude 호출)는 분석 실행기에서 트랜잭션 밖으로 돈다.
			if (!analysisService.startRetry(postId, version)) {
				return ConfirmResult.STALE;
			}
			analysisExecutor.execute(() -> analysisService.resume(postId));
			return ConfirmResult.STARTED;
		}
		if (state.failedStage() == PostStatus.PUBLISHING) {
			return publishService.retry(postId, version);
		}
		return ConfirmResult.STALE;
	}

	/** 웹 [확인]. 웹 담당 검토 대기 게시물만 게시를 시작한다. */
	public PostDetailResponse confirmFromWeb(Long id, int version) {
		checkWeb(id, version, PostStatus.PENDING_REVIEW);
		return toDetail(id, publishService.confirm(id, version));
	}

	/** 웹 [취소]. 웹 담당 검토 대기 게시물만 취소한다. */
	public PostDetailResponse cancelFromWeb(Long id, int version) {
		checkWeb(id, version, PostStatus.PENDING_REVIEW);
		return toDetail(id, cancel(id, version));
	}

	/** 웹 [재시도]. 웹 담당 실패 게시물만 다시 시도한다. */
	public PostDetailResponse retryFromWeb(Long id, int version) {
		checkWeb(id, version, PostStatus.FAILED);
		return toDetail(id, retry(id, version));
	}

	/** 없으면 404, 텔레그램 담당이면 POST_NOT_EDITABLE, 상태·버전이 다르면 POST_VERSION_CONFLICT. */
	private void checkWeb(Long id, int version, PostStatus expected) {
		State state = readState(id);
		if (state == null) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		if (state.channel() != PostChannel.WEB) {
			throw new BusinessException(ErrorCode.POST_NOT_EDITABLE);
		}
		if (state.status() != expected || state.version() != version) {
			throw new BusinessException(ErrorCode.POST_VERSION_CONFLICT);
		}
	}

	private PostDetailResponse toDetail(Long id, ConfirmResult result) {
		return switch (result) {
			case STARTED -> postQueryService.detail(id);
			case STALE -> throw new BusinessException(ErrorCode.POST_VERSION_CONFLICT);
			case CAPTION_TOO_LONG -> throw new BusinessException(ErrorCode.POST_CAPTION_TOO_LONG);
		};
	}

	private State readState(Long id) {
		return readTx.execute(status -> postRepository.findById(id)
			.map(PostActionService::stateOf)
			.orElse(null));
	}

	private static State stateOf(Post post) {
		return new State(post.getStatus(), post.getVersion(), post.getChannel(), post.getFailedStage());
	}

	private record State(PostStatus status, int version, PostChannel channel, PostStatus failedStage) {
	}

}
