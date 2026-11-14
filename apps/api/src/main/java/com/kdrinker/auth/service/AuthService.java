package com.kdrinker.auth.service;

import com.kdrinker.auth.Base32;
import com.kdrinker.auth.TotpVerifier;
import com.kdrinker.auth.entity.AuthSession;
import com.kdrinker.auth.entity.AuthState;
import com.kdrinker.auth.repository.AuthSessionRepository;
import com.kdrinker.auth.repository.AuthStateRepository;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.common.Hashes;
import com.kdrinker.common.Tokens;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty("app.auth.enabled")
public class AuthService {

	public static final Duration SESSION_LIFETIME = Duration.ofDays(90);
	private static final int MIN_SECRET_BYTES = 16;

	private final AuthSessionRepository sessionRepository;
	private final AuthStateRepository stateRepository;
	private final Clock clock;
	private final SecureRandom secureRandom;
	private final String secret;

	public AuthService(AuthSessionRepository sessionRepository, AuthStateRepository stateRepository, Clock clock,
			SecureRandom secureRandom, @Value("${app.auth.totp-secret}") String secret) {
		this.sessionRepository = sessionRepository;
		this.stateRepository = stateRepository;
		this.clock = clock;
		this.secureRandom = secureRandom;
		this.secret = requireValidSecret(secret);
	}

	/** 실패해도 실패 횟수와 잠금은 남아야 하므로 BusinessException에 롤백하지 않는다. */
	@Transactional(noRollbackFor = BusinessException.class)
	public String login(String code) {
		Instant instant = clock.instant();
		LocalDateTime now = LocalDateTime.ofInstant(instant, clock.getZone());
		AuthState state = loadStateForUpdate();
		if (state.isLockedAt(now)) {
			throw new BusinessException(ErrorCode.AUTH_LOCKED);
		}

		long step = TotpVerifier.matchStep(secret, code, instant.getEpochSecond());
		if (step == -1 || state.isStepAlreadyUsed(step)) {
			state.recordFailure(now);
			throw new BusinessException(state.isLockedAt(now) ? ErrorCode.AUTH_LOCKED : ErrorCode.AUTH_INVALID_CODE);
		}

		state.recordSuccess(step);
		String token = Tokens.random(secureRandom);
		sessionRepository.save(new AuthSession(sha256(token), now, now.plus(SESSION_LIFETIME)));
		return token;
	}

	public boolean isValid(String token) {
		if (token == null) {
			return false;
		}
		LocalDateTime now = LocalDateTime.now(clock);
		return sessionRepository.findById(sha256(token))
				.filter(session -> session.isActiveAt(now))
				.isPresent();
	}

	@Transactional
	public void logout(String token) {
		if (token != null) {
			sessionRepository.deleteById(sha256(token));
		}
	}

	/** 기동 시 호출한다. 분실 대응으로 시크릿을 바꾸면 이전 세션과 잠금을 모두 지운다. */
	@Transactional
	public void revokeSessionsIfSecretChanged() {
		AuthState state = loadStateForUpdate();
		String fingerprint = sha256(secret);
		if (!fingerprint.equals(state.getSecretFingerprint())) {
			sessionRepository.deleteAllInBatch();
			state.replaceSecret(fingerprint);
		}
	}

	private AuthState loadStateForUpdate() {
		return stateRepository.findForUpdate()
				.orElseThrow(() -> new IllegalStateException("auth_state 행이 없습니다."));
	}

	private static String requireValidSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("인증이 켜져 있으면 TOTP_SECRET이 필요합니다.");
		}
		byte[] decoded;
		try {
			decoded = Base32.decode(secret);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("TOTP_SECRET이 Base32 형식이 아닙니다.");
		}
		if (decoded.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("TOTP_SECRET은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
		}
		return secret;
	}

	private static String sha256(String value) {
		return Hashes.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
	}

}
