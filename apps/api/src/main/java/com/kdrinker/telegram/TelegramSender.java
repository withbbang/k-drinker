package com.kdrinker.telegram;

import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.entity.NotificationKind;
import com.kdrinker.telegram.entity.TelegramNotification;
import com.kdrinker.telegram.repository.TelegramNotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 알림 전송기. 중복 키가 있으면 먼저 기록 행을 넣어 같은 알림이 두 번 나가지 않게 하고, 실패하면 5초 간격으로 다시 시도한다.
 * HTTP 호출은 트랜잭션 밖에서 하고 DB 쓰기는 저장소 호출 단위로 짧게 끝낸다.
 */
@Slf4j
@Component
public class TelegramSender {

	private final TelegramClient client;
	private final TelegramNotificationRepository repository;
	private final Clock clock;
	private final Duration retryDelay;
	private final int maxRetries;

	public TelegramSender(TelegramClient client, TelegramNotificationRepository repository, Clock clock,
			@Value("${app.telegram.send-retry-delay}") Duration retryDelay,
			@Value("${app.telegram.send-max-retries}") int maxRetries) {
		this.client = client;
		this.repository = repository;
		this.clock = clock;
		this.retryDelay = retryDelay;
		this.maxRetries = maxRetries;
	}

	/** 보낸 message_id. 설정이 없거나 이미 보냈거나 끝내 실패하면 비어 있다. */
	public Optional<Long> send(String dedupKeyOrNull, NotificationKind kind, Long postIdOrNull,
			TelegramText text) {
		if (!client.isConfigured()) {
			return Optional.empty();
		}
		// 키 없는 알림도 message_id를 남겨 messageIds로 찾을 수 있게 행은 만든다.
		String key = dedupKeyOrNull != null ? dedupKeyOrNull : kind + ":" + UUID.randomUUID();
		TelegramNotification row = new TelegramNotification(key, postIdOrNull, kind, LocalDateTime.now(clock));
		if (!insert(row)) {
			return Optional.empty();
		}
		Optional<Long> messageId = sendWithRetry(text);
		if (messageId.isEmpty()) {
			repository.delete(row);
			log.warn("텔레그램 알림 전송을 포기했습니다. kind={}, postId={}", kind, postIdOrNull);
			return Optional.empty();
		}
		row.recordMessageId(messageId.get());
		repository.save(row);
		return messageId;
	}

	public List<Long> messageIds(Long postId, NotificationKind kind) {
		return repository.findByPostIdAndKindAndMessageIdIsNotNullOrderByIdAsc(postId, kind)
			.stream()
			.map(TelegramNotification::getMessageId)
			.toList();
	}

	/** 같은 키가 이미 있으면 false. 동시에 들어온 경우는 유니크 위반으로 걸러진다. */
	private boolean insert(TelegramNotification row) {
		if (repository.existsByDedupKey(row.getDedupKey())) {
			return false;
		}
		try {
			repository.saveAndFlush(row);
			return true;
		} catch (DataIntegrityViolationException e) {
			return false;
		}
	}

	private Optional<Long> sendWithRetry(TelegramText text) {
		for (int attempt = 0; attempt <= maxRetries; attempt++) {
			try {
				return Optional.of(client.sendMessage(text));
			} catch (RuntimeException e) {
				log.warn("텔레그램 전송 실패 ({}/{}): {}", attempt + 1, maxRetries + 1, e.getMessage());
				if (attempt < maxRetries && !pause()) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	private boolean pause() {
		if (retryDelay.isZero()) {
			return true;
		}
		try {
			Thread.sleep(retryDelay.toMillis());
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
