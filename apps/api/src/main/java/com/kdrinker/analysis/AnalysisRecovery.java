package com.kdrinker.analysis;

import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostAnalyzedEvent;
import com.kdrinker.post.repository.PostRepository;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 복구. 프로세스가 죽어 ANALYZING에 멈춘 게시물은 UNKNOWN 사유로 FAILED로 바꾸고(재시도는 사용자가 한다),
 * 분석이 시작되지 않은 RECEIVED 게시물은 분석 실행기에 다시 넣는다. 기동 스레드에서 분석을 돌리지 않는다.
 */
@Slf4j
@Component
public class AnalysisRecovery {

	private final PostRepository postRepository;
	private final AnalysisService analysisService;
	private final Executor analysisExecutor;
	private final ApplicationEventPublisher events;

	public AnalysisRecovery(PostRepository postRepository, AnalysisService analysisService,
			@Qualifier(AnalysisExecutorConfig.EXECUTOR) Executor analysisExecutor, ApplicationEventPublisher events) {
		this.postRepository = postRepository;
		this.analysisService = analysisService;
		this.analysisExecutor = analysisExecutor;
		this.events = events;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recover() {
		for (Long id : postRepository.findIdsByStatus(PostStatus.ANALYZING)) {
			try {
				if (analysisService.recordFailure(id, AnalysisFailure.UNKNOWN)) {
					log.warn("분석 중 멈춘 게시물을 실패로 바꿨습니다. postId={}", id);
					events.publishEvent(new PostAnalyzedEvent(id, PostStatus.FAILED));
				}
			} catch (RuntimeException e) {
				log.error("멈춘 게시물을 실패로 바꾸지 못했습니다. postId={}", id, e);
			}
		}
		for (Long id : postRepository.findIdsByStatus(PostStatus.RECEIVED)) {
			analysisExecutor.execute(() -> analysisService.analyze(id, PostStatus.RECEIVED));
		}
	}
}
