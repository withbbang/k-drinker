package com.kdrinker.publish;

import com.kdrinker.caption.CaptionAssembler;
import com.kdrinker.instagram.InstagramApiException;
import com.kdrinker.instagram.InstagramClient;
import com.kdrinker.instagram.InstagramClient.ContainerStatus;
import com.kdrinker.instagram.InstagramProperties;
import com.kdrinker.instagram.service.InstagramTokenService;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostPublishFinishedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PUBLISHING 게시물을 인스타그램에 올린다. 트랜잭션 밖에서 돌고 DB 쓰기만 짧은 트랜잭션으로 한다.
 * <p>
 * 중복 게시 방지: (부모) 컨테이너 id를 publish 호출 전에 커밋한다. 재시도는 저장된 컨테이너의 상태를 먼저 보고,
 * 이미 PUBLISHED면 다시 올리지 않는다.
 */
@Slf4j
@Component
public class PublishWorker {

	private static final int MAX_PHOTOS = 10;

	private final PostRepository postRepository;
	private final PostPhotoRepository photoRepository;
	private final PhotoService photoService;
	private final InstagramClient client;
	private final InstagramTokenService tokenService;
	private final InstagramProperties properties;
	private final PublishSleeper sleeper;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final TransactionTemplate tx;

	public PublishWorker(PostRepository postRepository, PostPhotoRepository photoRepository, PhotoService photoService,
			InstagramClient client, InstagramTokenService tokenService, InstagramProperties properties,
			PublishSleeper sleeper, ApplicationEventPublisher events, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.postRepository = postRepository;
		this.photoRepository = photoRepository;
		this.photoService = photoService;
		this.client = client;
		this.tokenService = tokenService;
		this.properties = properties;
		this.sleeper = sleeper;
		this.events = events;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
	}

	/** 예외를 밖으로 던지지 않는다. 상태를 바꿨으면 이벤트를 정확히 한 번 낸다. */
	public void publish(Long postId) {
		PostStatus result;
		try {
			Snapshot snapshot = tx.execute(status -> snapshot(postId));
			if (snapshot == null) {
				return;
			}
			try {
				run(postId, snapshot);
				result = PostStatus.PUBLISHED;
			} catch (Exception e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				result = fail(postId, e);
			}
		} catch (RuntimeException e) {
			log.error("게시 준비 중 예외가 났습니다. postId={}", postId, e);
			return;
		}
		if (result != null) {
			try {
				events.publishEvent(new PostPublishFinishedEvent(postId, result));
			} catch (RuntimeException e) {
				log.error("게시 완료 이벤트 처리 중 예외가 났습니다. postId={}", postId, e);
			}
		}
	}

	/** PUBLISHING 게시물을 실패로 바꾼다. 다른 상태면 아무것도 바꾸지 않고 false. 기동 복구도 쓴다. */
	public boolean recordFailure(Long postId, PublishFailure reason) {
		return Boolean.TRUE.equals(tx.execute(status -> {
			Post post = postRepository.findById(postId).orElse(null);
			if (post == null || post.getStatus() != PostStatus.PUBLISHING) {
				return false;
			}
			post.recordFailure(PostStatus.PUBLISHING, reason.message());
			if (postRepository.transition(postId, PostStatus.PUBLISHING, PostStatus.FAILED, clock.instant()) == 0) {
				status.setRollbackOnly();
				return false;
			}
			return true;
		}));
	}

	private Snapshot snapshot(Long postId) {
		Post post = postRepository.findById(postId).orElse(null);
		if (post == null || post.getStatus() != PostStatus.PUBLISHING) {
			return null;
		}
		List<PostPhoto> photos = photoRepository.findByPostIdOrderByPositionAsc(postId);
		List<String> urls = new ArrayList<>(photos.size());
		for (PostPhoto photo : photos) {
			urls.add(photoService.publicUrl(photo));
		}
		String caption = post.getCaption() == null ? "" : CaptionAssembler.stripMarkers(post.getCaption());
		return new Snapshot(caption, post.getIgContainerId(), urls);
	}

	private void run(Long postId, Snapshot snapshot) throws InterruptedException {
		String token = tokenService.currentToken();
		String containerId = snapshot.containerId();
		ContainerStatus state = null;
		if (containerId != null) {
			state = existingState(token, containerId);
			if (state == ContainerStatus.IN_PROGRESS) {
				state = waitForContainer(token, containerId);
			}
		}
		if (state == ContainerStatus.PUBLISHED) {
			log.warn("이미 게시된 컨테이너입니다. 다시 올리지 않고 게시됨으로 표시합니다. postId={}", postId);
			finish(postId, null, null);
			return;
		}
		if (state != ContainerStatus.FINISHED) {
			containerId = createContainers(postId, token, snapshot);
			waitForContainer(token, containerId);
		}
		String mediaId = client.publish(token, containerId);
		finish(postId, mediaId, permalinkOrNull(token, mediaId));
	}

	/** 저장된 컨테이너의 상태. 조회에 실패하면 null(새로 만든다). ERROR·EXPIRED도 새로 만들어야 하므로 null. */
	private ContainerStatus existingState(String token, String containerId) {
		try {
			ContainerStatus state = client.containerStatus(token, containerId);
			return state == ContainerStatus.ERROR || state == ContainerStatus.EXPIRED ? null : state;
		} catch (InstagramApiException e) {
			log.warn("저장된 컨테이너를 조회하지 못해 새로 만듭니다. cause={}", e.getMessage());
			return null;
		}
	}

	private String createContainers(Long postId, String token, Snapshot snapshot) {
		List<String> urls = snapshot.imageUrls();
		if (urls.isEmpty() || urls.size() > MAX_PHOTOS) {
			throw new IllegalStateException("게시할 사진 수가 올바르지 않습니다. postId=" + postId + " count=" + urls.size());
		}
		String containerId;
		if (urls.size() == 1) {
			containerId = client.createImageContainer(token, urls.get(0), false, snapshot.caption());
		} else {
			List<String> children = new ArrayList<>(urls.size());
			for (String url : urls) {
				children.add(client.createImageContainer(token, url, true, null));
			}
			containerId = client.createCarouselContainer(token, children, snapshot.caption());
		}
		// publish 전에 커밋한다. 이후 어디서 죽어도 재시도가 이 컨테이너의 상태를 확인할 수 있다.
		String saved = containerId;
		tx.executeWithoutResult(status -> postRepository.findById(postId).orElseThrow().recordContainer(saved));
		return containerId;
	}

	/**
	 * 컨테이너가 FINISHED·PUBLISHED가 될 때까지 첫 조회 후 polling 간격으로 조회한다. ERROR·EXPIRED는 이미지 실패,
	 * 시간 초과는 알 수 없음. 조회마다 건너뛴 시간을 합산해 timeout까지만 기다린다.
	 */
	private ContainerStatus waitForContainer(String token, String containerId) throws InterruptedException {
		Duration interval = properties.containerPollInterval();
		if (interval == null || interval.isZero() || interval.isNegative()) {
			interval = Duration.ofSeconds(1);
		}
		Duration timeout = properties.containerTimeout();
		Duration waited = Duration.ZERO;
		while (true) {
			ContainerStatus state = client.containerStatus(token, containerId);
			switch (state) {
				case FINISHED, PUBLISHED -> {
					return state;
				}
				case ERROR, EXPIRED -> throw new PublishAbort(PublishFailure.IMAGE_FETCH);
				case IN_PROGRESS -> {
				}
			}
			if (waited.compareTo(timeout) >= 0) {
				throw new PublishAbort(PublishFailure.UNKNOWN);
			}
			sleeper.sleep(interval);
			waited = waited.plus(interval);
		}
	}

	private String permalinkOrNull(String token, String mediaId) {
		try {
			return client.permalink(token, mediaId).orElse(null);
		} catch (RuntimeException e) {
			log.warn("게시물 링크를 조회하지 못했습니다. mediaId={}", mediaId, e);
			return null;
		}
	}

	/** PUBLISHING → PUBLISHED. 이미 올라간 뒤이므로 갱신이 0건이면 예외로 알린다. */
	private void finish(Long postId, String mediaId, String permalink) {
		tx.executeWithoutResult(status -> {
			Post post = postRepository.findById(postId).orElseThrow();
			post.recordPublished(mediaId, permalink, clock.instant());
			if (postRepository.transition(postId, PostStatus.PUBLISHING, PostStatus.PUBLISHED, clock.instant()) == 0) {
				throw new IllegalStateException("게시 중 게시물 상태가 바뀌었습니다. postId=" + postId);
			}
		});
	}

	/** 실패를 기록한다. 기록조차 실패하면 로그만 남기고 null(PUBLISHING으로 남아 다음 기동 때 복구된다). */
	private PostStatus fail(Long postId, Exception e) {
		PublishFailure reason = classify(e);
		if (reason == PublishFailure.UNKNOWN && !(e instanceof PublishAbort)) {
			log.error("게시 실패(알 수 없음) postId={}", postId, e);
		} else {
			log.warn("게시 실패 postId={} reason={} cause={}", postId, reason, e.toString());
		}
		try {
			return recordFailure(postId, reason) ? PostStatus.FAILED : null;
		} catch (RuntimeException recordError) {
			log.error("게시 실패를 기록하지 못했습니다. 게시물이 PUBLISHING으로 남습니다. postId={}", postId, recordError);
			return null;
		}
	}

	private static PublishFailure classify(Exception e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof PublishAbort abort) {
				return abort.reason;
			}
			if (t instanceof InstagramApiException api) {
				return api.classify();
			}
		}
		return PublishFailure.UNKNOWN;
	}

	private record Snapshot(String caption, String containerId, List<String> imageUrls) {
	}

	/** 사유가 정해진 실패. 스택트레이스가 필요 없다. */
	private static final class PublishAbort extends RuntimeException {

		private final transient PublishFailure reason;

		PublishAbort(PublishFailure reason) {
			super(reason.message(), null, false, false);
			this.reason = reason;
		}
	}
}
