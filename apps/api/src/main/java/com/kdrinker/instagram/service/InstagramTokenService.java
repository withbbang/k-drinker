package com.kdrinker.instagram.service;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.instagram.InstagramApiException;
import com.kdrinker.instagram.InstagramClient;
import com.kdrinker.instagram.InstagramClient.RefreshedToken;
import com.kdrinker.instagram.InstagramProperties;
import com.kdrinker.instagram.entity.InstagramToken;
import com.kdrinker.instagram.repository.InstagramTokenRepository;
import com.kdrinker.telegram.MessageFormatter;
import com.kdrinker.telegram.TelegramSender;
import com.kdrinker.telegram.entity.NotificationKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 인스타그램 장기 토큰의 보관과 갱신. 토큰 값은 로그나 응답에 내보내지 않는다.
 * HTTP 호출은 트랜잭션 밖이고 저장은 저장소 호출 하나로 끝난다.
 */
@Slf4j
@Service
public class InstagramTokenService {

	/** 첫 갱신이 실패해 실제 만료를 모를 때 쓰는 보수적 값. 다음 날 refreshIfDue가 다시 시도한다. */
	private static final Duration UNKNOWN_EXPIRY = Duration.ofDays(1);
	/** 발급·갱신 후 이만큼 지나야 다시 갱신할 수 있다. */
	private static final Duration MIN_AGE = Duration.ofHours(24);

	private final InstagramTokenRepository repository;
	private final InstagramClient client;
	private final TelegramSender sender;
	private final Clock clock;
	private final InstagramProperties properties;

	public InstagramTokenService(InstagramTokenRepository repository, InstagramClient client, TelegramSender sender,
			Clock clock, InstagramProperties properties) {
		this.repository = repository;
		this.client = client;
		this.sender = sender;
		this.clock = clock;
		this.properties = properties;
	}

	/** 게시에 쓸 토큰. 없으면 인증 실패(AUTH)로 다룬다. */
	public String currentToken() {
		return repository.findById(InstagramToken.SINGLE_ROW_ID)
			.map(InstagramToken::getAccessToken)
			.filter(token -> !token.isBlank())
			.orElseThrow(() -> new InstagramApiException(190, null, "저장된 인스타그램 토큰이 없습니다."));
	}

	public TokenStatus status() {
		InstagramToken row = repository.findById(InstagramToken.SINGLE_ROW_ID)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		return new TokenStatus(row.getExpiresAt(), row.getRefreshedAt());
	}

	/** 기동 시 행이 없고 환경변수가 있으면 그 토큰으로 행을 만든다. 실패해도 기동은 계속한다. */
	@EventListener(ApplicationReadyEvent.class)
	public void initIfMissing() {
		try {
			if (repository.existsById(InstagramToken.SINGLE_ROW_ID) || isBlank(properties.accessToken())) {
				return;
			}
			store(properties.accessToken());
		} catch (RuntimeException e) {
			log.warn("인스타그램 토큰 초기화에 실패했습니다. {}", e.getClass().getSimpleName());
		}
	}

	@Scheduled(cron = "${app.instagram.refresh-cron}", zone = "${app.instagram.zone}")
	public void refreshIfDue() {
		try {
			repository.findById(InstagramToken.SINGLE_ROW_ID).ifPresent(this::refreshRow);
		} catch (RuntimeException e) {
			log.warn("인스타그램 토큰 갱신 점검에 실패했습니다. {}", e.getClass().getSimpleName());
		}
	}

	/** 환경변수 토큰으로 저장된 값을 덮고 갱신을 한 번 시도한다. */
	public TokenStatus reload() {
		if (isBlank(properties.accessToken())) {
			throw new BusinessException(ErrorCode.INSTAGRAM_TOKEN_MISSING);
		}
		InstagramToken row = store(properties.accessToken());
		return new TokenStatus(row.getExpiresAt(), row.getRefreshedAt());
	}

	private void refreshRow(InstagramToken row) {
		Instant now = clock.instant();
		if (row.getExpiresAt().isAfter(now.plus(properties.refreshBefore()))
				|| row.getRefreshedAt().plus(MIN_AGE).isAfter(now)) {
			return;
		}
		try {
			RefreshedToken refreshed = client.refresh(row.getAccessToken());
			row.refresh(refreshed.accessToken(), now.plusSeconds(refreshed.expiresInSeconds()), now);
			repository.save(row);
			log.info("인스타그램 토큰을 갱신했습니다. expiresAt={}", row.getExpiresAt());
		} catch (InstagramApiException e) {
			log.warn("인스타그램 토큰 갱신에 실패했습니다. code={}, subcode={}", e.getCode(), e.getSubcode());
			LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
			sender.send("TOKEN_REFRESH_FAILED:" + today, NotificationKind.TOKEN_REFRESH_FAILED, null,
					MessageFormatter.tokenRefreshFailed(row.getExpiresAt()));
		}
	}

	/** 갱신을 시도해 실제 만료를 받고, 어떤 이유로든 실패하면 만료일을 모르므로 1일 뒤로 저장한다. */
	private InstagramToken store(String envToken) {
		Instant now = clock.instant();
		String token = envToken;
		Instant expiresAt = now.plus(UNKNOWN_EXPIRY);
		try {
			RefreshedToken refreshed = client.refresh(envToken);
			token = refreshed.accessToken();
			expiresAt = now.plusSeconds(refreshed.expiresInSeconds());
		} catch (InstagramApiException e) {
			log.info("토큰 갱신에 실패해 만료일을 1일 뒤로 저장합니다. code={}, subcode={}", e.getCode(), e.getSubcode());
		}
		InstagramToken row = repository.findById(InstagramToken.SINGLE_ROW_ID).orElse(null);
		if (row == null) {
			row = InstagramToken.issue(token, expiresAt, now);
		} else {
			row.refresh(token, expiresAt, now);
		}
		repository.save(row);
		return row;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
