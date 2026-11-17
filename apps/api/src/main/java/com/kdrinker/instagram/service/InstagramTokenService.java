package com.kdrinker.instagram.service;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.instagram.InstagramApiException;
import com.kdrinker.instagram.InstagramProperties;
import com.kdrinker.instagram.entity.InstagramToken;
import com.kdrinker.instagram.repository.InstagramTokenRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 인스타그램 액세스 토큰의 보관. 통합 로그인의 시스템 사용자 토큰은 만료되지 않아 갱신하지 않고, 만료 시각도 비워 둔다.
 * 토큰이 무효가 되면 게시 단계에서 인증 실패(190)로 드러난다. 토큰 값은 로그나 응답에 내보내지 않는다.
 */
@Slf4j
@Service
public class InstagramTokenService {

	private final InstagramTokenRepository repository;
	private final Clock clock;
	private final InstagramProperties properties;

	public InstagramTokenService(InstagramTokenRepository repository, Clock clock, InstagramProperties properties) {
		this.repository = repository;
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

	/** 환경변수 토큰으로 저장된 값을 덮는다. */
	public TokenStatus reload() {
		if (isBlank(properties.accessToken())) {
			throw new BusinessException(ErrorCode.INSTAGRAM_TOKEN_MISSING);
		}
		InstagramToken row = store(properties.accessToken());
		return new TokenStatus(row.getExpiresAt(), row.getRefreshedAt());
	}

	private InstagramToken store(String envToken) {
		Instant now = clock.instant();
		InstagramToken row = repository.findById(InstagramToken.SINGLE_ROW_ID).orElse(null);
		if (row == null) {
			row = InstagramToken.issue(envToken, null, now);
		} else {
			row.refresh(envToken, null, now);
		}
		repository.save(row);
		log.info("인스타그램 토큰을 저장했습니다.");
		return row;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
