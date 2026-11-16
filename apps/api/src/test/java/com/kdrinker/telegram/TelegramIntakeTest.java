package com.kdrinker.telegram;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.service.PostIntakeService;
import com.kdrinker.telegram.AlbumCollector.PendingAlbum;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramIntakeTest {

	private TelegramClient client;
	private TelegramSender sender;
	private PostIntakeService intake;
	private AlbumCollector collector;
	private TelegramIntake telegramIntake;

	@BeforeEach
	void setUp() {
		client = mock(TelegramClient.class);
		sender = mock(TelegramSender.class);
		intake = mock(PostIntakeService.class);
		collector = mock(AlbumCollector.class);
		when(client.isConfigured()).thenReturn(true);
		telegramIntake = new TelegramIntake(collector, client, sender, intake);
	}

	@Test
	void 열한_장은_내려받지_않고_거절한다() {
		List<String> ids = IntStream.rangeClosed(1, 11).mapToObj(i -> "F" + i).toList();

		telegramIntake.process(List.of(new PendingAlbum(100L, ids, "메모")));

		verify(client, never()).downloadFile(anyString());
		verify(intake, never()).receive(any(), any(), any(), any());
		verify(sender).send(eq(null), eq(NotificationKind.TOO_MANY_PHOTOS), eq(null),
				eq(MessageFormatter.tooManyPhotos(11)));
	}

	@Test
	void 텔레그램_출처로_접수한다() {
		byte[] b1 = {1};
		byte[] b2 = {2};
		when(client.downloadFile("F1")).thenReturn(b1);
		when(client.downloadFile("F2")).thenReturn(b2);

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1", "F2"), "메모")));

		verify(intake).receive(PostSource.TELEGRAM, "메모", null, List.of(b1, b2));
	}

	@Test
	void 크기_초과_사진이_있으면_그_앨범은_접수하지_않는다() {
		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1", "F2"), null,
				java.util.Arrays.asList(100L, 21L * 1024 * 1024))));

		verify(client, never()).downloadFile(anyString());
		verify(intake, never()).receive(any(), any(), any(), any());
	}

	@Test
	void 업무_예외가_나도_다음_앨범을_처리한다() {
		when(client.downloadFile(anyString())).thenReturn(new byte[] {1});
		when(intake.receive(any(), eq("a"), any(), any())).thenThrow(new BusinessException(ErrorCode.POST_INVALID_RATING));

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1"), "a"),
				new PendingAlbum(100L, List.of("F2"), "b")));

		verify(intake, times(2)).receive(any(), any(), any(), any());
	}

	@Test
	void 미설정이면_예약_작업은_아무것도_하지_않는다() {
		when(client.isConfigured()).thenReturn(false);

		telegramIntake.drainDue();

		verify(collector, never()).drainDue();
	}

}
