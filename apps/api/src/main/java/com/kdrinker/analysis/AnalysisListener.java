package com.kdrinker.analysis;

import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 접수가 커밋된 뒤 분석 실행기에서 분석을 돌린다. */
@Component
@RequiredArgsConstructor
public class AnalysisListener {

	private final AnalysisService analysisService;

	@Async(AnalysisExecutorConfig.EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReceived(PostReceivedEvent event) {
		analysisService.analyze(event.postId(), PostStatus.RECEIVED);
	}
}
