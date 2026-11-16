package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
import com.kdrinker.telegram.repository.TelegramNotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TelegramSenderTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);
	private static final TelegramText TEXT = TelegramText.of("안녕");

	@Autowired
	private TelegramNotificationRepository repository;

	private TelegramClient client;
	private TelegramSender sender;

	@BeforeEach
	void setUp() {
		client = mock(TelegramClient.class);
		when(client.isConfigured()).thenReturn(true);
		sender = new TelegramSender(client, repository, CLOCK, Duration.ZERO, 3);
	}

	@Test
	void 같은_키는_한_번만_보낸다() {
		// given
		when(client.sendMessage(any())).thenReturn(77L);

		// when
		Optional<Long> first = sender.send("OVERVIEW:1:2", NotificationKind.OVERVIEW, 1L, TEXT);
		Optional<Long> second = sender.send("OVERVIEW:1:2", NotificationKind.OVERVIEW, 1L, TEXT);

		// then
		assertThat(first).contains(77L);
		assertThat(second).isEmpty();
		verify(client, times(1)).sendMessage(any());
	}

	@Test
	void 세_번_다시_시도하고_그래도_실패하면_포기하며_행을_지운다() {
		// given
		when(client.sendMessage(any())).thenThrow(new TelegramApiException("실패"));

		// when
		Optional<Long> result = sender.send("RECEIVED:1", NotificationKind.RECEIVED, 1L, TEXT);

		// then
		assertThat(result).isEmpty();
		verify(client, times(4)).sendMessage(any());
		assertThat(repository.existsByDedupKey("RECEIVED:1")).isFalse();
	}

	@Test
	void 포기한_뒤_같은_키로_다시_보내면_다시_시도한다() {
		// given
		when(client.sendMessage(any())).thenThrow(new TelegramApiException("실패")).thenThrow(
				new TelegramApiException("실패"), new TelegramApiException("실패"), new TelegramApiException("실패"))
			.thenReturn(5L);
		sender.send("RECEIVED:1", NotificationKind.RECEIVED, 1L, TEXT);

		// when
		Optional<Long> again = sender.send("RECEIVED:1", NotificationKind.RECEIVED, 1L, TEXT);

		// then
		assertThat(again).contains(5L);
	}

	@Test
	void 중간에_성공하면_거기서_멈춘다() {
		// given
		when(client.sendMessage(any())).thenThrow(new TelegramApiException("실패")).thenReturn(9L);

		// when
		Optional<Long> result = sender.send(null, NotificationKind.DETAIL, 1L, TEXT);

		// then
		assertThat(result).contains(9L);
		verify(client, times(2)).sendMessage(any());
	}

	@Test
	void 성공하면_message_id를_기록한다() {
		// given
		when(client.sendMessage(any())).thenReturn(77L);

		// when
		sender.send("OVERVIEW:1:2", NotificationKind.OVERVIEW, 1L, TEXT);

		// then
		assertThat(sender.messageIds(1L, NotificationKind.OVERVIEW)).containsExactly(77L);
		assertThat(sender.messageIds(1L, NotificationKind.DETAIL)).isEqualTo(List.of());
	}

	@Test
	void 키가_없으면_매번_보낸다() {
		// given
		when(client.sendMessage(any())).thenReturn(1L, 2L);

		// when
		sender.send(null, NotificationKind.DETAIL, 1L, TEXT);
		sender.send(null, NotificationKind.DETAIL, 1L, TEXT);

		// then
		assertThat(sender.messageIds(1L, NotificationKind.DETAIL)).containsExactly(1L, 2L);
	}

	@Test
	void 설정이_없으면_행도_호출도_없다() {
		// given
		when(client.isConfigured()).thenReturn(false);

		// when
		Optional<Long> result = sender.send("RECEIVED:1", NotificationKind.RECEIVED, 1L, TEXT);

		// then
		assertThat(result).isEmpty();
		verify(client, never()).sendMessage(any());
		assertThat(repository.count()).isZero();
	}

}
