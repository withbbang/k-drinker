package com.kdrinker.telegram;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.publish.PublishExecutorConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 기동 알림 보정. 종료 중 알림 실행기가 먼저 멈춰 개요(버튼)·실패 알림이 버려졌을 수 있으므로, 최근 7일 안에 갱신된
 * PENDING_REVIEW·FAILED 게시물의 알림을 다시 요청한다. 중복 키가 게시물 version을 담고 있어 이미 나간 알림은
 * TelegramSender가 건너뛰고(개요는 이전 버튼도 건드리지 않는다), 못 나간 것만 나간다.
 * 게시 복구와 같은 실행기의 대기열 뒤에 넣어(마지막 순서) 복구가 만든 FAILED도 함께 다룬다.
 */
@Slf4j
@Component
public class NotificationReconciler {

	static final Duration WINDOW = Duration.ofDays(7);

	private final PostRepository postRepository;
	private final PostNotifier notifier;
	private final Executor executor;
	private final Clock clock;

	public NotificationReconciler(PostRepository postRepository, PostNotifier notifier,
			@Qualifier(PublishExecutorConfig.EXECUTOR) Executor publishExecutor, Clock clock) {
		this.postRepository = postRepository;
		this.notifier = notifier;
		this.executor = publishExecutor;
		this.clock = clock;
	}

	@Order(Ordered.LOWEST_PRECEDENCE)
	@EventListener(ApplicationReadyEvent.class)
	public void reconcile() {
		executor.execute(this::reconcileNow);
	}

	void reconcileNow() {
		var since = clock.instant().minus(WINDOW);
		try {
			for (Long id : postRepository.findIdsByStatusUpdatedSince(PostStatus.PENDING_REVIEW, since)) {
				notifier.notifyReviewReady(id);
			}
			for (Long id : postRepository.findIdsByStatusUpdatedSince(PostStatus.FAILED, since)) {
				Post post = postRepository.findById(id).orElse(null);
				if (post == null) {
					continue;
				}
				if (post.getFailedStage() == PostStatus.ANALYZING) {
					notifier.notifyAnalysisFailed(id);
				} else if (post.getFailedStage() == PostStatus.PUBLISHING) {
					notifier.notifyPublishFinished(id);
				}
			}
		} catch (RuntimeException e) {
			log.warn("기동 알림 보정에 실패했습니다: {}", e.getMessage());
		}
	}

}
