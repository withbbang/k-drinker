package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.kdrinker.post.service.PhotoInput;
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
		// 접수 시점에 각 사진의 바이트를 읽어 모으고, 임시 파일이 접수 중에는 있다가 끝나면 지워지는지 본다.
		when(intake.receiveInputs(any(), any(), any(), any())).thenAnswer(inv -> {
			List<PhotoInput> inputs = inv.getArgument(3);
			for (PhotoInput input : inputs == null ? List.<PhotoInput>of() : inputs) {
				received.add(input.readAllBytes());
			}
			return null;
		});
	}

	private final List<byte[]> received = new java.util.ArrayList<>();

	@Test
	void 열한_장은_내려받지_않고_거절한다() {
		List<String> ids = IntStream.rangeClosed(1, 11).mapToObj(i -> "F" + i).toList();

		telegramIntake.process(List.of(new PendingAlbum(100L, ids, "메모")));

		verify(client, never()).downloadFile(anyString());
		verify(intake, never()).receiveInputs(any(), any(), any(), any());
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

		verify(intake).receiveInputs(eq(PostSource.TELEGRAM), eq("메모"), eq(null), any());
		assertThat(received).containsExactly(b1, b2);
	}

	@Test
	void 크기_초과_사진은_빼고_접수하고_안내는_한_번만_보낸다() {
		byte[] b1 = {1};
		when(client.downloadFile("F1")).thenReturn(b1);

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1", "F2", "F3"), null,
				java.util.Arrays.asList(100L, 21L * 1024 * 1024, 22L * 1024 * 1024))));

		verify(client, never()).downloadFile("F2");
		verify(intake).receiveInputs(eq(PostSource.TELEGRAM), eq(null), eq(null), any());
		assertThat(received).containsExactly(b1);
		verify(sender, times(1)).send(eq(null), eq(NotificationKind.INTAKE_FAILED), eq(null),
				eq(MessageFormatter.photoTooLarge()));
	}

	@Test
	void 전부_크기_초과면_접수하지_않고_안내만_보낸다() {
		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1"), null,
				java.util.Arrays.asList(21L * 1024 * 1024))));

		verify(intake, never()).receiveInputs(any(), any(), any(), any());
		verify(sender).send(eq(null), eq(NotificationKind.INTAKE_FAILED), eq(null),
				eq(MessageFormatter.photoTooLarge()));
	}

	@Test
	void 다운로드가_실패하면_접수하지_않고_안내를_보낸다() {
		when(client.downloadFile("F1")).thenThrow(new TelegramApiException("fail"));

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1"), "메모")));

		verify(intake, never()).receiveInputs(any(), any(), any(), any());
		verify(sender).send(eq(null), eq(NotificationKind.INTAKE_FAILED), eq(null),
				eq(MessageFormatter.intakeFailed()));
	}

	@Test
	void 앨범에서_한_장만_다운로드에_실패하면_나머지로_접수하고_안내는_한_번만() {
		byte[] b1 = {1};
		byte[] b3 = {3};
		when(client.downloadFile("F1")).thenReturn(b1);
		when(client.downloadFile("F2")).thenThrow(new TelegramApiException("fail"));
		when(client.downloadFile("F3")).thenReturn(b3);

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1", "F2", "F3"), null)));

		verify(intake).receiveInputs(eq(PostSource.TELEGRAM), eq(null), eq(null), any());
		assertThat(received).containsExactly(b1, b3);
		verify(sender, times(1)).send(eq(null), eq(NotificationKind.INTAKE_FAILED), eq(null),
				eq(MessageFormatter.intakeFailed()));
	}

	@Test
	void 접수_업무_예외는_안내를_한_번_보낸다() {
		when(client.downloadFile(anyString())).thenReturn(new byte[] {1});
		when(intake.receiveInputs(any(), any(), any(), any())).thenThrow(new BusinessException(ErrorCode.POST_INVALID_RATING));

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1"), "a")));

		verify(sender, times(1)).send(eq(null), eq(NotificationKind.INTAKE_FAILED), eq(null),
				eq(MessageFormatter.intakeFailed()));
	}

	@Test
	void 정상_접수는_실패_안내를_보내지_않는다() {
		when(client.downloadFile(anyString())).thenReturn(new byte[] {1});

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1"), "a")));

		verify(sender, never()).send(any(), any(), any(), any());
	}

	@Test
	void 업무_예외가_나도_다음_앨범을_처리한다() {
		when(client.downloadFile(anyString())).thenReturn(new byte[] {1});
		when(intake.receiveInputs(any(), eq("a"), any(), any())).thenThrow(new BusinessException(ErrorCode.POST_INVALID_RATING));

		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1"), "a"),
				new PendingAlbum(100L, List.of("F2"), "b")));

		verify(intake, times(2)).receiveInputs(any(), any(), any(), any());
	}

	@Test
	void 미설정이면_예약_작업은_아무것도_하지_않는다() {
		when(client.isConfigured()).thenReturn(false);

		telegramIntake.drainDue();

		verify(collector, never()).drainDue();
	}

	@Test
	void 사진은_다운로드한_순서대로_파일로_넘기고_접수가_끝나면_임시_파일을_지운다() {
		// given
		java.util.List<String> order = new java.util.ArrayList<>();
		when(client.downloadFile(anyString())).thenAnswer(inv -> {
			order.add("download:" + inv.getArgument(0));
			return new byte[] {1};
		});
		java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>();
		when(intake.receiveInputs(any(), any(), any(), any())).thenAnswer(inv -> {
			order.add("receive");
			List<PhotoInput> inputs = inv.getArgument(3);
			for (PhotoInput input : inputs == null ? List.<PhotoInput>of() : inputs) {
				// 접수 중에는 파일에서 한 장씩 읽을 수 있다.
				assertThat(input.size()).isEqualTo(1);
				assertThat(input.readAllBytes()).containsExactly(1);
			}
			try (var list = java.nio.file.Files.list(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")))) {
				list.filter(f -> f.getFileName().toString().startsWith("tg-intake-")).forEach(files::add);
			}
			return null;
		});

		// when
		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1", "F2", "F3"), null)));

		// then
		assertThat(order).containsExactly("download:F1", "download:F2", "download:F3", "receive");
		assertThat(files).isNotEmpty().allSatisfy(f -> assertThat(f).doesNotExist());
	}

	@Test
	void 다운로드_실패와_크기_초과가_겹치면_순서와_상관없이_20MB_안내를_보낸다() {
		// given
		when(client.downloadFile("F1")).thenThrow(new TelegramApiException("fail"));

		// when
		telegramIntake.process(List.of(new PendingAlbum(100L, List.of("F1", "F2"), null,
				java.util.Arrays.asList(100L, 25L * 1024 * 1024))));

		// then
		verify(sender, times(1)).send(eq(null), eq(NotificationKind.INTAKE_FAILED), eq(null),
				eq(MessageFormatter.photoTooLarge()));
	}

}
