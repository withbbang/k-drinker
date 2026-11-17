package com.kdrinker.instagram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.instagram.InstagramApiException;
import com.kdrinker.instagram.InstagramProperties;
import com.kdrinker.instagram.entity.InstagramToken;
import com.kdrinker.instagram.repository.InstagramTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InstagramTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-15T16:00:00Z");

	private final InstagramTokenRepository repository = mock(InstagramTokenRepository.class);
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private InstagramTokenService service(String envToken) {
		InstagramProperties props = new InstagramProperties("https://graph.facebook.com", "v27.0", envToken, "1789",
				Duration.ofSeconds(3), Duration.ofMinutes(5));
		return new InstagramTokenService(repository, clock, props);
	}

	@Test
	void 행이_없고_환경변수가_있으면_만료_없이_그대로_저장한다() {
		// given
		when(repository.existsById(InstagramToken.SINGLE_ROW_ID)).thenReturn(false);

		// when
		service("ENV").initIfMissing();

		// then
		ArgumentCaptor<InstagramToken> saved = ArgumentCaptor.forClass(InstagramToken.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getAccessToken()).isEqualTo("ENV");
		assertThat(saved.getValue().getExpiresAt()).isNull();
		assertThat(saved.getValue().getRefreshedAt()).isEqualTo(NOW);
	}

	@Test
	void 행이_있으면_환경변수를_쓰지_않는다() {
		// given
		when(repository.existsById(InstagramToken.SINGLE_ROW_ID)).thenReturn(true);

		// when
		service("ENV").initIfMissing();

		// then
		verify(repository, never()).save(any());
	}

	@Test
	void 환경변수가_비면_초기화하지_않는다() {
		// given
		when(repository.existsById(InstagramToken.SINGLE_ROW_ID)).thenReturn(false);

		// when
		service("").initIfMissing();

		// then
		verify(repository, never()).save(any());
	}

	@Test
	void 초기화_중_저장이_실패해도_예외를_던지지_않는다() {
		// given
		when(repository.existsById(InstagramToken.SINGLE_ROW_ID)).thenThrow(new IllegalStateException("db down"));

		// when / then
		service("ENV").initIfMissing();
		verify(repository, never()).save(any());
	}

	@Test
	void currentToken은_저장된_토큰을_돌려준다() {
		// given
		when(repository.findById(InstagramToken.SINGLE_ROW_ID))
			.thenReturn(Optional.of(InstagramToken.issue("SAVED", null, NOW)));

		// when / then
		assertThat(service("ENV").currentToken()).isEqualTo("SAVED");
	}

	@Test
	void currentToken은_행이_없으면_AUTH_실패다() {
		// given
		when(repository.findById(InstagramToken.SINGLE_ROW_ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service("ENV").currentToken())
			.isInstanceOfSatisfying(InstagramApiException.class, e -> assertThat(e.getCode()).isEqualTo(190));
	}

	@Test
	void reload는_예전_만료값이_있던_행도_환경변수_토큰과_만료_없음으로_덮는다() {
		// given 갱신 방식 시절에 저장된 행
		InstagramToken row = InstagramToken.issue("OLD", NOW.plus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(59)));
		when(repository.findById(InstagramToken.SINGLE_ROW_ID)).thenReturn(Optional.of(row));

		// when
		TokenStatus status = service("ENV").reload();

		// then
		verify(repository).save(row);
		assertThat(row.getAccessToken()).isEqualTo("ENV");
		assertThat(status.expiresAt()).isNull();
		assertThat(status.refreshedAt()).isEqualTo(NOW);
	}

	@Test
	void reload는_환경변수가_비면_BusinessException이다() {
		assertThatThrownBy(() -> service("").reload()).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSTAGRAM_TOKEN_MISSING));
	}

}
