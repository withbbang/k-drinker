package com.kdrinker.publish;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.caption.CaptionAssembler;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostPublishFinishedEvent;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시 확정·재시도·캡션 길이 검사. 인스타그램 호출은 하지 않는다(게시 실행기의 {@link PublishWorker}가 한다).
 * 실행기 제출과 이벤트 발행은 커밋 뒤에 한다.
 */
@Slf4j
@Service
public class PublishService implements CaptionLengthGuard {

	static final int MAX_CAPTION_LENGTH = 2200;

	private final PostRepository postRepository;
	private final PublishWorker worker;
	private final Executor publishExecutor;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final TransactionTemplate tx;

	public PublishService(PostRepository postRepository, PublishWorker worker,
			@Qualifier(PublishExecutorConfig.EXECUTOR) Executor publishExecutor, ApplicationEventPublisher events,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.postRepository = postRepository;
		this.worker = worker;
		this.publishExecutor = publishExecutor;
		this.events = events;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
	}

	/** PENDING_REVIEW(버전 일치) → PUBLISHING 후 게시 실행기에 넣는다. 캡션이 넘치면 FAILED. */
	public ConfirmResult confirm(Long postId, int version) {
		return tx.execute(status -> {
			Post post = postRepository.findById(postId).orElse(null);
			if (post == null || post.getStatus() != PostStatus.PENDING_REVIEW || post.getVersion() != version) {
				return ConfirmResult.STALE;
			}
			if (tooLong(post)) {
				return failTooLong(status, post, version) ? ConfirmResult.CAPTION_TOO_LONG : ConfirmResult.STALE;
			}
			Brewery brewery = post.getDrink() == null ? null : post.getDrink().getBrewery();
			if (brewery != null) {
				brewery.markVerified();
			}
			// [확인]은 항상 새 내용이다. 이전 컨테이너를 쓰지 않도록 비운다(transition이 먼저 flush한다). [재시도]만 재사용한다.
			post.clearContainer();
			if (postRepository.transition(postId, PostStatus.PENDING_REVIEW, PostStatus.PUBLISHING, version,
					clock.instant()) == 0) {
				status.setRollbackOnly();
				return ConfirmResult.STALE;
			}
			afterCommit(() -> submit(postId));
			return ConfirmResult.STARTED;
		});
	}

	/** 게시 단계에서 실패한 게시물(캡션 초과 제외, 버전 일치)을 FAILED → PUBLISHING 후 게시 실행기에 넣는다. */
	public ConfirmResult retry(Long postId, int version) {
		return tx.execute(status -> {
			Post post = postRepository.findById(postId).orElse(null);
			if (post == null || post.getStatus() != PostStatus.FAILED || post.getVersion() != version
					|| post.getFailedStage() != PostStatus.PUBLISHING
					|| PublishFailure.CAPTION_TOO_LONG.message().equals(post.getFailureReason())) {
				return ConfirmResult.STALE;
			}
			post.clearFailure();
			if (postRepository.transition(postId, PostStatus.FAILED, PostStatus.PUBLISHING, version,
					clock.instant()) == 0) {
				status.setRollbackOnly();
				return ConfirmResult.STALE;
			}
			afterCommit(() -> submit(postId));
			return ConfirmResult.STARTED;
		});
	}

	@Override
	public boolean failIfTooLong(Long postId) {
		return Boolean.TRUE.equals(tx.execute(status -> {
			Post post = postRepository.findById(postId).orElse(null);
			if (post == null || post.getStatus() != PostStatus.PENDING_REVIEW || !tooLong(post)) {
				return false;
			}
			return failTooLong(status, post, null);
		}));
	}

	private static boolean tooLong(Post post) {
		return post.getCaption() != null && CaptionAssembler.length(post.getCaption()) > MAX_CAPTION_LENGTH;
	}

	/** PENDING_REVIEW → FAILED(게시 단계, 캡션 초과). version이 null이면 버전을 보지 않는다. 갱신 0건이면 롤백하고 false. */
	private boolean failTooLong(org.springframework.transaction.TransactionStatus status, Post post,
			Integer version) {
		Long postId = post.getId();
		post.recordFailure(PostStatus.PUBLISHING, PublishFailure.CAPTION_TOO_LONG.message());
		int updated = version == null
				? postRepository.transition(postId, PostStatus.PENDING_REVIEW, PostStatus.FAILED, clock.instant())
				: postRepository.transition(postId, PostStatus.PENDING_REVIEW, PostStatus.FAILED, version,
						clock.instant());
		if (updated == 0) {
			status.setRollbackOnly();
			return false;
		}
		afterCommit(() -> events.publishEvent(new PostPublishFinishedEvent(postId, PostStatus.FAILED)));
		return true;
	}

	private void submit(Long postId) {
		publishExecutor.execute(() -> worker.publish(postId));
	}

	/** 바깥 트랜잭션에 합류했더라도 커밋 뒤에 실행한다. 트랜잭션이 없으면 바로 실행한다. */
	private static void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}
}
