package com.kdrinker.telegram;

import com.kdrinker.analysis.AnalysisExecutorConfig;
import com.kdrinker.analysis.AnalysisService;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.publish.ConfirmResult;
import com.kdrinker.publish.PublishService;
import com.kdrinker.telegram.dto.CallbackQuery;
import com.kdrinker.telegram.entity.NotificationKind;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
	private final AnalysisService analysisService;
	private final PostNotifier notifier;
	private final PhotoStorage photoStorage;
	private final Executor analysisExecutor;
	private final Clock clock;
	private final String publicBaseUrl;
	private final TransactionTemplate tx;

	public CallbackHandler(TelegramClient client, TelegramSender sender, PostRepository postRepository,
			PostPhotoRepository photoRepository, PublishService publishService, AnalysisService analysisService,
			PostNotifier notifier, PhotoStorage photoStorage,
			@Qualifier(AnalysisExecutorConfig.EXECUTOR) Executor analysisExecutor, Clock clock,
			@Value("${app.public-base-url}") String publicBaseUrl, PlatformTransactionManager transactionManager) {
		this.client = client;
		this.sender = sender;
		this.postRepository = postRepository;
		this.photoRepository = photoRepository;
		this.publishService = publishService;
		this.analysisService = analysisService;
		this.notifier = notifier;
		this.photoStorage = photoStorage;
		this.analysisExecutor = analysisExecutor;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
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
		// 갱신 쿼리는 트랜잭션이 필요하다. 알림(HTTP)은 트랜잭션 밖에서 보낸다.
		Integer updated = tx.execute(status -> postRepository.transition(data.postId(), PostStatus.PENDING_REVIEW,
				PostStatus.CANCELED, data.version(), clock.instant()));
		if (updated == null || updated == 0) {
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
		if (post.getFailedStage() == PostStatus.ANALYZING) {
			// analyze가 조건부 전이로 경합을 막는다. 분석 실행기에서 트랜잭션 밖으로 돈다.
			analysisExecutor.execute(() -> analysisService.analyze(id, PostStatus.FAILED));
			return new Outcome(MessageFormatter.accepted("retry"), true);
		}
		if (post.getFailedStage() == PostStatus.PUBLISHING) {
			ConfirmResult result = publishService.retry(id, data.version());
			if (result == ConfirmResult.STALE) {
				return stale();
			}
			if (result == ConfirmResult.STARTED) {
				notifier.notifyPublishing(id);
				return new Outcome(MessageFormatter.accepted("retry"), true);
			}
			return new Outcome(null, true);
		}
		return stale();
	}

	private static Outcome stale() {
		return new Outcome(MessageFormatter.staleButton(), false);
	}

}
