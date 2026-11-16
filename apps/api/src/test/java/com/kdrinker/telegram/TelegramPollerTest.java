package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.telegram.dto.CallbackQuery;
import com.kdrinker.telegram.dto.TelegramMessage;
import com.kdrinker.telegram.dto.TelegramUpdate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class TelegramPollerTest {

	private static final long OWNER = 100L;

	private TelegramClient client;
	private TelegramSender sender;
	private AlbumCollector collector;
	private TelegramIntake intake;
	private CallbackSink sink;
	private TelegramPoller poller;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		client = mock(TelegramClient.class);
		sender = mock(TelegramSender.class);
		collector = mock(AlbumCollector.class);
		intake = mock(TelegramIntake.class);
		sink = mock(CallbackSink.class);
		ObjectProvider<CallbackSink> sinks = mock(ObjectProvider.class);
		when(sinks.getIfAvailable()).thenReturn(sink);
		when(client.isOwnerChat(anyLong())).thenAnswer(i -> (long) i.getArgument(0) == OWNER);
		TelegramProperties props = new TelegramProperties("u", "t", "100", 25, Duration.ofSeconds(3),
				Duration.ZERO, 3);
		poller = new TelegramPoller(client, sender, collector, intake, sinks, props);
	}

	@Test
	void 다른_chat의_업데이트는_버린다() {
		TelegramMessage other = new TelegramMessage(1, 999L, "hi", null, null, null, null);
		CallbackQuery otherCb = new CallbackQuery("c", 999L, 1L, "ok:1:1");
		when(client.getUpdates(anyLong(), anyInt()))
				.thenReturn(List.of(new TelegramUpdate(5, other, null), new TelegramUpdate(6, null, otherCb)));

		poller.pollOnce();

		verify(collector, never()).accept(any());
		verify(sender, never()).send(any(), any(), any(), any());
		verify(sink, never()).handle(any());
	}

	@Test
	void 텍스트만_온_메시지는_안내를_보낸다() {
		TelegramMessage m = new TelegramMessage(1, OWNER, "hi", null, null, null, null);
		when(client.getUpdates(anyLong(), anyInt())).thenReturn(List.of(new TelegramUpdate(5, m, null)));
		when(collector.accept(m)).thenReturn(new AlbumCollector.TextOnly());

		poller.pollOnce();

		verify(sender).send(eq(null), eq(com.kdrinker.telegram.entity.NotificationKind.TEXT_ONLY), eq(null),
				any());
	}

	@Test
	void 소유자_콜백은_싱크로_넘긴다() {
		CallbackQuery cb = new CallbackQuery("c", OWNER, 1L, "ok:1:1");
		when(client.getUpdates(anyLong(), anyInt())).thenReturn(List.of(new TelegramUpdate(5, null, cb)));

		poller.pollOnce();

		verify(sink).handle(cb);
	}

	@Test
	void 다음_호출은_마지막_id_더하기_1을_offset으로_쓴다() {
		when(client.getUpdates(anyLong(), anyInt())).thenReturn(
				List.of(new TelegramUpdate(10, null, null), new TelegramUpdate(11, null, null)), List.of());

		poller.pollOnce();
		poller.pollOnce();

		ArgumentCaptor<Long> offsets = ArgumentCaptor.forClass(Long.class);
		verify(client, org.mockito.Mockito.times(2)).getUpdates(offsets.capture(), anyInt());
		assertThat(offsets.getAllValues()).containsExactly(0L, 12L);
	}

	@Test
	void 미설정이면_시작하지_않는다() {
		when(client.isConfigured()).thenReturn(false);

		poller.start();

		assertThat(poller.isRunning()).isFalse();
	}

	@Test
	void 백오프는_5초에서_두_배씩_5분까지_늘고_리셋된다() {
		PollBackoff b = new PollBackoff();
		assertThat(b.nextDelay()).isEqualTo(Duration.ofSeconds(5));
		assertThat(b.nextDelay()).isEqualTo(Duration.ofSeconds(10));
		for (int i = 0; i < 10; i++) {
			b.nextDelay();
		}
		assertThat(b.nextDelay()).isEqualTo(Duration.ofMinutes(5));
		b.reset();
		assertThat(b.nextDelay()).isEqualTo(Duration.ofSeconds(5));
	}

}
