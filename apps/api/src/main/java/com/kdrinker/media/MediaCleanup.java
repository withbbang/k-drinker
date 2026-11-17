package com.kdrinker.media;

import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 새벽에 취소된 지 오래된 게시물의 사진과 오래된 임시(스테이징) 폴더를 지운다. 게시물 행은 기록으로 남긴다.
 * DB 행은 짧은 트랜잭션으로 지우고, 파일은 그 트랜잭션이 커밋된 뒤에만 지운다.
 */
@Slf4j
@Component
public class MediaCleanup {

	private final PostRepository postRepository;
	private final PostPhotoRepository postPhotoRepository;
	private final PhotoStorage photoStorage;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final Duration canceledRetention;
	private final Duration stagingRetention;

	public MediaCleanup(PostRepository postRepository, PostPhotoRepository postPhotoRepository,
			PhotoStorage photoStorage, PlatformTransactionManager transactionManager, Clock clock,
			@Value("${app.media.cleanup.canceled-retention}") Duration canceledRetention,
			@Value("${app.media.cleanup.staging-retention}") Duration stagingRetention) {
		this.postRepository = postRepository;
		this.postPhotoRepository = postPhotoRepository;
		this.photoStorage = photoStorage;
		this.tx = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.canceledRetention = canceledRetention;
		this.stagingRetention = stagingRetention;
	}

	@Scheduled(cron = "${app.media.cleanup.cron}", zone = "${app.media.cleanup.zone}")
	public void run() {
		Instant now = clock.instant();
		int posts = 0;
		int photos = 0;
		try {
			for (Long postId : postRepository.findIdsByStatusUpdatedBefore(PostStatus.CANCELED,
					now.minus(canceledRetention))) {
				try {
					int removed = cleanPost(postId);
					if (removed > 0) {
						posts++;
						photos += removed;
					}
				} catch (RuntimeException e) {
					log.warn("취소 게시물 사진 정리 실패 postId={}", postId, e);
				}
			}
		} catch (RuntimeException e) {
			log.warn("취소 게시물 조회 실패", e);
		}
		int staging = 0;
		try {
			staging = photoStorage.deleteStagingOlderThan(now.minus(stagingRetention));
		} catch (RuntimeException e) {
			log.warn("임시 폴더 정리 실패", e);
		}
		log.info("사진 정리: 취소 게시물 {}건, 사진 {}장, 임시 폴더 {}개", posts, photos, staging);
	}

	/** 사진 행을 지우고 커밋 뒤 파일을 지운다. 지운 사진 수(이미 없으면 0). */
	private int cleanPost(Long postId) {
		List<String> paths = new ArrayList<>();
		Integer removed = tx.execute(status -> {
			List<PostPhoto> photos = postPhotoRepository.findByPostIdOrderByPositionAsc(postId);
			for (PostPhoto photo : photos) {
				paths.add(photo.getOriginalPath());
				paths.add(photo.getRenderedPath());
			}
			postPhotoRepository.deleteAll(photos);
			return photos.size();
		});
		if (removed == null || removed == 0) {
			return 0;
		}
		for (String path : paths) {
			if (path != null) {
				photoStorage.deleteQuietly(path);
			}
		}
		photoStorage.deletePostOriginalDirQuietly(postId);
		return removed;
	}

}
