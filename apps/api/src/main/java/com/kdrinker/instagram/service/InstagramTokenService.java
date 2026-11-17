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

	/**
	 * 기동 시 환경변수 토큰이 있고 저장된 값과 다르면(행 없음, 다른 토큰, 예전 방식의 만료 시각이 남은 행) 환경변수로 맞춘다.
	 * 토큰을 바꾸는 경로가 환경변수뿐이라, reload를 빠뜨려도 재기동만으로 반영되게 한다. 실패해도 기동은 계속한다.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void syncFromEnv() {
		try {
			String envToken = properties.accessToken();
			if (isBlank(envToken)) {
				return;
			}
			InstagramToken row = repository.findById(InstagramToken.SINGLE_ROW_ID).orElse(null);
			if (row != null && envToken.equals(row.getAccessToken()) && row.getExpiresAt() == null) {
				return;
			}
			store(row, envToken);
		} catch (RuntimeException e) {
			log.warn("인스타그램 토큰 초기화에 실패했습니다. {}", e.getClass().getSimpleName());
		}
	}

	/** 환경변수 토큰으로 저장된 값을 덮는다. */
	public TokenStatus reload() {
		if (isBlank(properties.accessToken())) {
			throw new BusinessException(ErrorCode.INSTAGRAM_TOKEN_MISSING);
		}
		InstagramToken row = store(repository.findById(InstagramToken.SINGLE_ROW_ID).orElse(null),
				properties.accessToken());
		return new TokenStatus(row.getExpiresAt(), row.getRefreshedAt());
	}

	private InstagramToken store(InstagramToken rowOrNull, String envToken) {
		Instant now = clock.instant();
		InstagramToken row = rowOrNull;
		if (row == null) {
			row = InstagramToken.issue(envToken, now);
		} else {
			row.replace(envToken, now);
		}
		repository.save(row);
		log.info("인스타그램 토큰을 저장했습니다.");
		return row;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
