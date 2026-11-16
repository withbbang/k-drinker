package com.kdrinker.telegram;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.publish.CaptionLengthGuard;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시물 상태별 텔레그램 알림. DB 읽기는 읽기 전용 트랜잭션 안에서 문구까지 만들고, 전송은 트랜잭션 밖에서 한다.
 * 리스너가 이 빈을 거쳐 호출해야 {@code @Async}가 적용된다.
 */
@Slf4j
@Component
public class PostNotifier {

	private final TelegramSender sender;
	private final TelegramClient client;
	private final PostRepository postRepository;
	private final PostPhotoRepository photoRepository;
	private final CaptionLengthGuard captionLengthGuard;
	private final TransactionTemplate readTx;

	public PostNotifier(TelegramSender sender, TelegramClient client, PostRepository postRepository,
			PostPhotoRepository photoRepository, CaptionLengthGuard captionLengthGuard,
			TransactionTemplate transactionTemplate) {
		this.sender = sender;
		this.client = client;
		this.postRepository = postRepository;
		this.photoRepository = photoRepository;
		this.captionLengthGuard = captionLengthGuard;
		this.readTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
		this.readTx.setReadOnly(true);
	}

	/** 읽은 시점의 문구와 중복 키 재료. */
	private record Prepared(PostStatus status, int version, TelegramText text) {
	}

	@Async(TelegramExecutorConfig.EXECUTOR)
	public void notifyReceived(Long postId) {
		prepare(postId, (post) -> MessageFormatter.received(photoCount(postId)))
			.ifPresent(p -> sender.send("RECEIVED:" + postId, NotificationKind.RECEIVED, postId, p.text()));
	}

	/** P4의 수정 저장도 이 메서드를 부른다. */
	@Async(TelegramExecutorConfig.EXECUTOR)
	public void notifyReviewReady(Long postId) {
		if (captionLengthGuard.failIfTooLong(postId)) {
			return;
		}
		Optional<Prepared> prepared = prepare(postId, post -> post.getStatus() != PostStatus.PENDING_REVIEW ? null
				: MessageFormatter.overview(post, breweryOf(post), photoCount(postId)));
		if (prepared.isEmpty() || prepared.get().text() == null) {
			return;
		}
		Prepared p = prepared.get();
		Optional<Long> sent = sender.send("OVERVIEW:" + postId + ":" + p.version(), NotificationKind.OVERVIEW, postId,
				p.text());
		if (sent.isEmpty()) {
			return;
		}
		for (Long old : sender.messageIds(postId, NotificationKind.OVERVIEW)) {
			if (old.equals(sent.get())) {
				continue;
			}
			try {
				client.removeKeyboard(old);
			} catch (RuntimeException e) {
				log.warn("이전 개요 버튼 제거 실패. postId={}, messageId={}: {}", postId, old, e.getMessage());
			}
		}
	}

	@Async(TelegramExecutorConfig.EXECUTOR)
	public void notifyAnalysisFailed(Long postId) {
		prepare(postId, MessageFormatter::analysisFailed).ifPresent(p -> sender.send(
				"ANALYSIS_FAILED:" + postId + ":" + p.version(), NotificationKind.ANALYSIS_FAILED, postId, p.text()));
	}

	@Async(TelegramExecutorConfig.EXECUTOR)
	public void notifyPublishing(Long postId) {
		prepare(postId, MessageFormatter::publishing).ifPresent(p -> sender.send(
				"PUBLISHING:" + postId + ":" + p.version(), NotificationKind.PUBLISHING, postId, p.text()));
	}

	/** 게시물의 현재 상태로 PUBLISHED / PUBLISH_FAILED 를 고른다. */
	@Async(TelegramExecutorConfig.EXECUTOR)
	public void notifyPublishFinished(Long postId) {
		prepare(postId, post -> post.getStatus() == PostStatus.PUBLISHED ? MessageFormatter.published(post)
				: post.getStatus() == PostStatus.FAILED ? MessageFormatter.publishFailed(post) : null)
			.filter(p -> p.text() != null)
			.ifPresent(p -> {
				if (p.status() == PostStatus.PUBLISHED) {
					sender.send("PUBLISHED:" + postId, NotificationKind.PUBLISHED, postId, p.text());
				} else {
					sender.send("PUBLISH_FAILED:" + postId + ":" + p.version(), NotificationKind.PUBLISH_FAILED,
							postId, p.text());
				}
			});
	}

	@Async(TelegramExecutorConfig.EXECUTOR)
	public void notifyCanceled(Long postId) {
		prepare(postId, MessageFormatter::canceled)
			.ifPresent(p -> sender.send("CANCELED:" + postId, NotificationKind.CANCELED, postId, p.text()));
	}

	private Optional<Prepared> prepare(Long postId, Function<Post, TelegramText> formatter) {
		try {
			return Optional.ofNullable(readTx.execute(status -> postRepository.findById(postId)
				.map(post -> new Prepared(post.getStatus(), post.getVersion(), formatter.apply(post)))
				.orElse(null)));
		} catch (RuntimeException e) {
			log.warn("알림 준비 실패. postId={}: {}", postId, e.getMessage());
			return Optional.empty();
		}
	}

	private int photoCount(Long postId) {
		return photoRepository.findByPostIdOrderByPositionAsc(postId).size();
	}

	private static Brewery breweryOf(Post post) {
		Drink drink = post.getDrink();
		if (drink == null || drink.getBrewery() == null) {
			return null;
		}
		return Hibernate.unproxy(drink.getBrewery(), Brewery.class);
	}

}
