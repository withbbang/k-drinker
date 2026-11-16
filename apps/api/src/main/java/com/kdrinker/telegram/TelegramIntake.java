package com.kdrinker.telegram;

import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.service.PhotoInput;
import com.kdrinker.post.service.PostIntakeService;
import com.kdrinker.telegram.AlbumCollector.PendingAlbum;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감된 앨범을 내려받아 게시물로 접수한다. 다운로드와 접수는 트랜잭션 밖에서 하고(접수는 서비스가 자체 트랜잭션),
 * 사진은 한 장씩 받는다.
 */
@Slf4j
@Component
public class TelegramIntake {

	static final int MAX_PHOTOS = 10;
	static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

	private final AlbumCollector collector;
	private final TelegramClient client;
	private final TelegramSender sender;
	private final PostIntakeService intake;

	public TelegramIntake(AlbumCollector collector, TelegramClient client, TelegramSender sender,
			PostIntakeService intake) {
		this.collector = collector;
		this.client = client;
		this.sender = sender;
		this.intake = intake;
	}

	@Scheduled(fixedDelay = 500)
	public void drainDue() {
		if (!client.isConfigured()) {
			return;
		}
		process(collector.drainDue());
	}

	public void process(List<PendingAlbum> albums) {
		for (PendingAlbum album : albums) {
			try {
				processOne(album);
			} catch (RuntimeException e) {
				log.warn("텔레그램 앨범 처리 실패", e);
			}
		}
	}

	/**
	 * 받을 수 있는 사진만 모아 접수한다. 일부 사진이 크기 초과·다운로드 실패면 나머지로 게시물을 만들고, 앨범마다 실패 안내는
	 * 한 번만 보낸다. 원인이 겹치면 20MB 초과 문구가 우선이고(문서 13번), 접수 자체가 실패하면 마지막 문구를 쓴다.
	 * 받을 수 있는 사진이 없거나 접수가 실패하면 게시물은 만들지 않는다.
	 * <p>
	 * 사진은 한 장씩 내려받아 곧바로 임시 파일에 쓰고 바이트는 놓는다(메모리에는 한 장만). 다운로드(HTTP)는 접수 트랜잭션 밖에서
	 * 끝내고, 접수 서비스가 트랜잭션 안에서 파일을 한 장씩 읽는다. 그때 파일 읽기가 실패하면 앨범 전체 접수가 실패한다.
	 */
	private void processOne(PendingAlbum album) {
		int count = album.fileIds().size();
		if (count > MAX_PHOTOS) {
			sender.send(null, NotificationKind.TOO_MANY_PHOTOS, null, MessageFormatter.tooManyPhotos(count));
			return;
		}
		boolean tooLarge = false;
		boolean downloadFailed = false;
		boolean intakeFailed = false;
		Path dir = null;
		try {
			List<PhotoInput> photos = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				Long size = album.fileSizes().get(i);
				if (size != null && size > MAX_FILE_BYTES) {
					log.warn("텔레그램 사진이 20MB를 넘어 건너뜁니다. size={}", size);
					tooLarge = true;
					continue;
				}
				try {
					if (dir == null) {
						dir = Files.createTempDirectory("tg-intake-");
					}
					photos.add(download(dir, i, album.fileIds().get(i)));
				} catch (IOException | RuntimeException e) {
					log.warn("텔레그램 사진 다운로드 실패", e);
					downloadFailed = true;
				}
			}
			if (!photos.isEmpty()) {
				try {
					intake.receiveInputs(PostSource.TELEGRAM, album.memo(), null, photos);
				} catch (RuntimeException e) {
					log.warn("텔레그램 앨범 접수 실패", e);
					intakeFailed = true;
				}
			}
		} finally {
			deleteQuietly(dir);
		}
		TelegramText failure = intakeFailed ? MessageFormatter.intakeFailed()
				: tooLarge ? MessageFormatter.photoTooLarge() : downloadFailed ? MessageFormatter.intakeFailed() : null;
		if (failure != null) {
			sender.send(null, NotificationKind.INTAKE_FAILED, null, failure);
		}
	}

	/** 한 장을 내려받아 임시 파일에 쓴다. 바이트 배열은 이 메서드가 끝나면 참조가 사라진다. */
	private PhotoInput download(Path dir, int index, String fileId) throws IOException {
		byte[] bytes = client.downloadFile(fileId);
		Path file = dir.resolve(index + ".bin");
		Files.write(file, bytes);
		return new FilePhotoInput(file, bytes.length);
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null) {
			return;
		}
		try (var files = Files.list(dir)) {
			files.forEach(f -> f.toFile().delete());
		} catch (IOException | RuntimeException e) {
			log.warn("임시 사진 파일을 지우지 못했습니다. dir={}", dir);
		}
		dir.toFile().delete();
	}

	private record FilePhotoInput(Path file, long size) implements PhotoInput {

		@Override
		public byte[] readAllBytes() throws IOException {
			return Files.readAllBytes(file);
		}
	}

}
