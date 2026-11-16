package com.kdrinker.telegram;

import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.service.PostIntakeService;
import com.kdrinker.telegram.AlbumCollector.PendingAlbum;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
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
	 * 한 번만 보낸다. 받을 수 있는 사진이 없거나 접수가 실패하면 게시물은 만들지 않는다.
	 */
	private void processOne(PendingAlbum album) {
		int count = album.fileIds().size();
		if (count > MAX_PHOTOS) {
			sender.send(null, NotificationKind.TOO_MANY_PHOTOS, null, MessageFormatter.tooManyPhotos(count));
			return;
		}
		TelegramText failure = null;
		List<byte[]> photos = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Long size = album.fileSizes().get(i);
			if (size != null && size > MAX_FILE_BYTES) {
				log.warn("텔레그램 사진이 20MB를 넘어 건너뜁니다. size={}", size);
				failure = failure == null ? MessageFormatter.photoTooLarge() : failure;
				continue;
			}
			try {
				photos.add(client.downloadFile(album.fileIds().get(i)));
			} catch (RuntimeException e) {
				log.warn("텔레그램 사진 다운로드 실패", e);
				failure = failure == null ? MessageFormatter.intakeFailed() : failure;
			}
		}
		if (!photos.isEmpty()) {
			try {
				intake.receive(PostSource.TELEGRAM, album.memo(), null, photos);
			} catch (RuntimeException e) {
				log.warn("텔레그램 앨범 접수 실패", e);
				failure = MessageFormatter.intakeFailed();
			}
		}
		if (failure != null) {
			sender.send(null, NotificationKind.INTAKE_FAILED, null, failure);
		}
	}

}
