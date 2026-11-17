package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdrinker.post.event.PostEditedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class PostNotificationListenerTest {

	private final PostNotifier notifier = mock(PostNotifier.class);
	private final PostNotificationListener listener = new PostNotificationListener(notifier);

	@Test
	void 수정_이벤트는_커밋_뒤_새_개요를_보낸다() throws Exception {
		// given
		TransactionalEventListener annotation = PostNotificationListener.class
				.getMethod("onEdited", PostEditedEvent.class).getAnnotation(TransactionalEventListener.class);

		// when
		listener.onEdited(new PostEditedEvent(7L));

		// then
		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		verify(notifier).notifyReviewReady(7L);
	}
}
