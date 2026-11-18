package com.kdrinker.telegram;

import com.kdrinker.media.PhotoStorage;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostChannel;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.post.service.PostActionService;
import com.kdrinker.publish.ConfirmResult;
import com.kdrinker.publish.PublishService;
import com.kdrinker.telegram.dto.CallbackQuery;
import com.kdrinker.telegram.entity.NotificationKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 소유자 chat의 인라인 버튼 콜백을 처리한다. 폴러 스레드에서 불리며 텔레그램 HTTP 호출을 트랜잭션 밖에서 한다.
 * 어떤 경로든 answerCallback은 한 번만 보낸다. 예외는 로그만 남기고 폴러로 던지지 않는다.
 */
@Slf4j
@Component
public class CallbackHandler implements CallbackSink {

	/** 팝업 문구(없으면 null)와 누른 메시지의 버튼 제거 여부. */
	private record Outcome(String popup, boolean removeButtons) {

		static final Outcome NONE = new Outcome(null, false);
	}

	private final TelegramClient client;
	private final TelegramSender sender;
	private final PostRepository postRepository;
	private final PostPhotoRepository photoRepository;
	private final PublishService publishService;
	private final PostActionService postActionService;
	private final PostNotifier notifier;
	private final PhotoStorage photoStorage;
	private final String publicBaseUrl;

	public CallbackHandler(TelegramClient client, TelegramSender sender, PostRepository postRepository,
			PostPhotoRepository photoRepository, PublishService publishService, PostActionService postActionService,
			PostNotifier notifier, PhotoStorage photoStorage, @Value("${app.public-base-url}") String publicBaseUrl) {
		this.client = client;
		this.sender = sender;
		this.postRepository = postRepository;
		this.photoRepository = photoRepository;
		this.publishService = publishService;
		this.postActionService = postActionService;
		this.notifier = notifier;
		this.photoStorage = photoStorage;
		this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
				: publicBaseUrl;
	}

	@Override
	public void handle(CallbackQuery query) {
		if (!client.isOwnerChat(query.fromChatId())) {
			return;
		}
		Outcome outcome;
		try {
			outcome = dispatch(query);
		} catch (RuntimeException e) {
			log.warn("버튼 처리 실패: data={}", query.data(), e);
			outcome = Outcome.NONE;
		}
		try {
			client.answerCallback(query.id(), outcome.popup());
		} catch (RuntimeException e) {
			log.warn("버튼 응답 실패: {}", e.getMessage());
		}
		if (outcome.removeButtons() && query.messageId() != null) {
			try {
				client.removeKeyboard(query.messageId());
			} catch (RuntimeException e) {
				log.warn("버튼 제거 실패: messageId={}: {}", query.messageId(), e.getMessage());
			}
		}
	}

	private Outcome dispatch(CallbackQuery query) {
		Optional<CallbackData> parsed = CallbackData.parse(query.data());
		if (parsed.isEmpty()) {
			return stale();
		}
		CallbackData data = parsed.get();
		Post post = postRepository.findById(data.postId()).orElse(null);
		if (post == null || post.getVersion() != data.version()) {
			return stale();
		}
		if (post.getChannel() == PostChannel.WEB) {
			// 웹에서 저장해 웹 담당이 된 게시물. 남아 있는 텔레그램 버튼으로는 아무것도 하지 않는다.
			return stale();
		}
		return switch (data.act()) {
			case "ok" -> ok(post, data);
			case "cancel" -> cancel(post, data);
			case "detail" -> detail(post);
			case "edit" -> edit(post);
			case "retry" -> retry(post, data);
			default -> stale();
		};
	}

	private Outcome ok(Post post, CallbackData data) {
		if (post.getStatus() != PostStatus.PENDING_REVIEW) {
			return stale();
		}
		ConfirmResult result = publishService.confirm(data.postId(), data.version());
		return switch (result) {
			case STARTED -> {
				notifier.notifyPublishing(data.postId());
				yield new Outcome(MessageFormatter.accepted("ok"), true);
			}
			// 실패 알림은 이벤트 리스너가 보낸다.
			case CAPTION_TOO_LONG -> new Outcome(null, true);
			case STALE -> stale();
		};
	}

	private Outcome cancel(Post post, CallbackData data) {
		if (post.getStatus() != PostStatus.PENDING_REVIEW) {
			return stale();
		}
		// 전이는 서비스가 자기 트랜잭션에서 한다. 알림(HTTP)은 트랜잭션 밖에서 보낸다.
		if (postActionService.cancel(data.postId(), data.version()) != ConfirmResult.STARTED) {
			return stale();
		}
		notifier.notifyCanceled(data.postId());
		return new Outcome(MessageFormatter.accepted("cancel"), true);
	}

	private Outcome detail(Post post) {
		if (post.getStatus() != PostStatus.PENDING_REVIEW) {
			return stale();
		}
		List<Path> photos = photoRepository.findByPostIdOrderByPositionAsc(post.getId())
			.stream()
			.map(PostPhoto::getRenderedPath)
			.filter(path -> path != null)
			.map(photoStorage::locate)
			.toList();
		if (!photos.isEmpty()) {
			client.sendPhotos(photos);
		}
		sender.send(null, NotificationKind.DETAIL, post.getId(), MessageFormatter.detailCaption(post));
		return Outcome.NONE;
	}

	private Outcome edit(Post post) {
		if (post.getStatus() != PostStatus.PENDING_REVIEW && post.getStatus() != PostStatus.FAILED) {
			return stale();
		}
		String url = publicBaseUrl + "/posts/" + post.getId() + "/edit";
		sender.send(null, NotificationKind.EDIT_LINK, post.getId(), MessageFormatter.editLink(url));
		return Outcome.NONE;
	}

	private Outcome retry(Post post, CallbackData data) {
		if (post.getStatus() != PostStatus.FAILED) {
			return stale();
		}
		Long id = data.postId();
		ConfirmResult result = postActionService.retry(id, data.version());
		return switch (result) {
			case STARTED -> {
				// 게시 단계 재시도만 "게시 중" 알림을 보낸다. 재분석은 팝업만 띄운다.
				if (post.getFailedStage() == PostStatus.PUBLISHING) {
					notifier.notifyPublishing(id);
				}
				yield new Outcome(MessageFormatter.accepted("retry"), true);
			}
			case CAPTION_TOO_LONG -> new Outcome(null, true);
			case STALE -> stale();
		};
	}

	private static Outcome stale() {
		return new Outcome(MessageFormatter.staleButton(), false);
	}

}
