package com.kdrinker.publish;

import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostPublishFinishedEvent;
import com.kdrinker.post.repository.PostRepository;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 복구. 프로세스가 죽어 PUBLISHING에 멈춘 게시물은 UNKNOWN 사유로 FAILED로 바꾼다. 게시를 자동으로 이어가지 않는다
 * (중복 게시 방지). 컨테이너 id는 남으므로 사용자가 [재시도]하면 인스타그램의 컨테이너 상태를 먼저 확인한다.
 * 게시 실행기에 넣어 이미 들어온 게시 작업이 끝난 뒤에 돈다.
 */
@Slf4j
@Component
public class PublishRecovery {

	private final PostRepository postRepository;
	private final PublishWorker worker;
	private final Executor publishExecutor;
	private final ApplicationEventPublisher events;

	public PublishRecovery(PostRepository postRepository, PublishWorker worker,
			@Qualifier(PublishExecutorConfig.EXECUTOR) Executor publishExecutor, ApplicationEventPublisher events) {
		this.postRepository = postRepository;
		this.worker = worker;
		this.publishExecutor = publishExecutor;
		this.events = events;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recover() {
		publishExecutor.execute(this::recoverNow);
	}

	private void recoverNow() {
		for (Long id : postRepository.findIdsByStatus(PostStatus.PUBLISHING)) {
			try {
				if (worker.recordFailure(id, PublishFailure.UNKNOWN)) {
					log.warn("게시 중 멈춘 게시물을 실패로 바꿨습니다. postId={}", id);
					events.publishEvent(new PostPublishFinishedEvent(id, PostStatus.FAILED));
				}
			} catch (RuntimeException e) {
				log.error("멈춘 게시물을 실패로 바꾸지 못했습니다. postId={}", id, e);
			}
		}
	}
}
