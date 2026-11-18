package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.kdrinker.post.event.PostEditedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class PostNotificationListenerTest {

	private final PostNotifier notifier = mock(PostNotifier.class);
	private final PostNotificationListener listener = new PostNotificationListener(notifier);

	@Test
	void 수정_이벤트는_커밋_뒤에_받는다() throws Exception {
		// when
		TransactionalEventListener annotation = PostNotificationListener.class
				.getMethod("onEdited", PostEditedEvent.class).getAnnotation(TransactionalEventListener.class);

		// then
		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	@Test
	void 웹으로_넘겨받은_수정은_텔레그램_개요_버튼만_지운다() {
		// when
		listener.onEdited(new PostEditedEvent(1L, true));

		// then
		verify(notifier).removeOverviewButtons(1L);
		verifyNoMoreInteractions(notifier);
	}

	@Test
	void 이미_웹_담당인_수정은_아무것도_보내지_않는다() {
		// when
		listener.onEdited(new PostEditedEvent(1L, false));

		// then
		verifyNoInteractions(notifier);
	}
}
