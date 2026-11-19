package com.kdrinker.telegram;

import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostAnalyzedEvent;
import com.kdrinker.post.event.PostHandedOverEvent;
import com.kdrinker.post.event.PostPublishFinishedEvent;
import com.kdrinker.post.event.PostReceivedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 게시물 이벤트를 알림으로 잇는다. 비동기는 PostNotifier 쪽 프록시가 처리한다. */
@Component
public class PostNotificationListener {

	private final PostNotifier notifier;

	public PostNotificationListener(PostNotifier notifier) {
		this.notifier = notifier;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReceived(PostReceivedEvent event) {
		notifier.notifyReceived(event.postId());
	}

	/** 웹 담당으로 넘어간 변경(웹 저장·양조장 수정)이 커밋된 뒤, 텔레그램 버튼을 지우고 웹 안내를 보낸다. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onHandedOver(PostHandedOverEvent event) {
		notifier.handOverToWeb(event.postId());
	}

	@EventListener
	public void onAnalyzed(PostAnalyzedEvent event) {
		if (event.result() == PostStatus.PENDING_REVIEW) {
			notifier.notifyReviewReady(event.postId());
		} else if (event.result() == PostStatus.FAILED) {
			notifier.notifyAnalysisFailed(event.postId());
		}
	}

	@EventListener
	public void onPublishFinished(PostPublishFinishedEvent event) {
		notifier.notifyPublishFinished(event.postId());
	}

}
