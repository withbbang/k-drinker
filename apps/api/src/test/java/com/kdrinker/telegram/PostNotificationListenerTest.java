package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.kdrinker.post.event.PostHandedOverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class PostNotificationListenerTest {

	private final PostNotifier notifier = mock(PostNotifier.class);
	private final PostNotificationListener listener = new PostNotificationListener(notifier);

	@Test
	void 웹_전환_이벤트는_커밋_뒤에_받는다() throws Exception {
		// when
		TransactionalEventListener annotation = PostNotificationListener.class
				.getMethod("onHandedOver", PostHandedOverEvent.class).getAnnotation(TransactionalEventListener.class);

		// then
		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	@Test
	void onHandedOver_callsNotifier() {
		// when
		listener.onHandedOver(new PostHandedOverEvent(1L));

		// then
		verify(notifier).handOverToWeb(1L);
		verifyNoMoreInteractions(notifier);
	}
}
