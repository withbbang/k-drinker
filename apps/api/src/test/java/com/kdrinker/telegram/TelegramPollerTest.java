package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
	void 지원하지_않는_첨부는_안내를_보낸다() {
		TelegramMessage m = new TelegramMessage(1, OWNER, null, null, null, null, null);
		when(client.getUpdates(anyLong(), anyInt())).thenReturn(List.of(new TelegramUpdate(5, m, null)));
		when(collector.accept(m)).thenReturn(new AlbumCollector.Unsupported(null));

		poller.pollOnce();

		verify(sender).send(eq(null), eq(com.kdrinker.telegram.entity.NotificationKind.INTAKE_FAILED), eq(null),
				eq(MessageFormatter.unsupportedAttachment()));
	}

	@Test
	void 같은_앨범의_지원하지_않는_첨부_안내는_한_번만_보낸다() {
		TelegramMessage m1 = new TelegramMessage(1, OWNER, null, null, "g1", null, null);
		TelegramMessage m2 = new TelegramMessage(2, OWNER, null, null, "g1", null, null);
		when(client.getUpdates(anyLong(), anyInt()))
				.thenReturn(List.of(new TelegramUpdate(5, m1, null), new TelegramUpdate(6, m2, null)));
		when(collector.accept(any())).thenReturn(new AlbumCollector.Unsupported("g1"));

		poller.pollOnce();

		verify(sender, times(1)).send(eq(null), eq(com.kdrinker.telegram.entity.NotificationKind.INTAKE_FAILED),
				eq(null), any());
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
	void 실행_중_stop은_대기_앨범을_접수로_넘긴다() {
		when(client.isConfigured()).thenReturn(true);
		when(client.getUpdates(anyLong(), anyInt())).thenAnswer(i -> {
			Thread.sleep(10);
			return List.of();
		});
		List<AlbumCollector.PendingAlbum> pending = List
				.of(new AlbumCollector.PendingAlbum(OWNER, List.of("F1"), null));
		when(collector.drainAll()).thenReturn(pending);

		poller.start();
		poller.stop();

		verify(intake).process(pending);
		assertThat(poller.isRunning()).isFalse();
	}

	@Test
	void 시작하지_않았으면_stop은_아무것도_접수하지_않는다() {
		poller.stop();

		verify(intake, never()).process(any());
	}

	@Test
	void 종료_중에는_받아둔_업데이트를_수집기에_넣지_않는다() throws Exception {
		when(client.isConfigured()).thenReturn(true);
		TelegramMessage m = new TelegramMessage(1, OWNER, "hi", null, null, null, null);
		java.util.concurrent.CountDownLatch inPoll = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		when(client.getUpdates(anyLong(), anyInt())).thenAnswer(i -> {
			inPoll.countDown();
			awaitUninterruptibly(release);
			return List.of(new TelegramUpdate(5, m, null));
		});

		poller.start();
		inPoll.await();
		Thread stopper = new Thread(poller::stop);
		stopper.start();
		// stop()이 stopping을 세우고 폴러 스레드 join에 들어갈 때까지 기다린다.
		while (stopper.getState() != Thread.State.TIMED_WAITING) {
			Thread.sleep(5);
		}
		release.countDown();
		stopper.join();

		verify(collector, never()).accept(any());
	}

	private static void awaitUninterruptibly(java.util.concurrent.CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
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
