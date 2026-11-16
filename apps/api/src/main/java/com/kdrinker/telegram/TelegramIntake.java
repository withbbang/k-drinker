package com.kdrinker.telegram;

import com.kdrinker.common.BusinessException;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.service.PostIntakeService;
import com.kdrinker.telegram.AlbumCollector.PendingAlbum;
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
			} catch (BusinessException e) {
				log.warn("텔레그램 앨범 접수 실패", e);
			} catch (RuntimeException e) {
				log.warn("텔레그램 앨범 처리 실패", e);
			}
		}
	}

	private void processOne(PendingAlbum album) {
		int count = album.fileIds().size();
		if (count > MAX_PHOTOS) {
			sender.send(null, NotificationKind.TOO_MANY_PHOTOS, null, MessageFormatter.tooManyPhotos(count));
			return;
		}
		for (Long size : album.fileSizes()) {
			if (size != null && size > MAX_FILE_BYTES) {
				log.warn("텔레그램 사진이 20MB를 넘어 앨범 접수를 건너뜁니다. size={}", size);
				return;
			}
		}
		List<byte[]> photos = new ArrayList<>(count);
		for (String fileId : album.fileIds()) {
			photos.add(client.downloadFile(fileId));
		}
		intake.receive(PostSource.TELEGRAM, album.memo(), null, photos);
	}

}
