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
import org.springframework.test.util.ReflectionTestUtils;

class InstagramTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-15T16:00:00Z");
	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private final InstagramTokenRepository repository = mock(InstagramTokenRepository.class);
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private InstagramTokenService service(String envToken) {
		InstagramProperties props = new InstagramProperties("https://graph.facebook.com", "v27.0", envToken, "1789",
				Duration.ofSeconds(3), Duration.ofMinutes(5));
		return new InstagramTokenService(repository, clock, props);
	}

	/** 갱신 방식 시절에 저장된 행: Instagram Login 토큰과 만료 시각이 남아 있다. */
	private static InstagramToken legacyRow() {
		InstagramToken row = InstagramToken.issue("IGAAOLD", EARLIER);
		ReflectionTestUtils.setField(row, "expiresAt", NOW.plus(Duration.ofDays(1)));
		return row;
	}

	private void givenRow(InstagramToken rowOrNull) {
		when(repository.findById(InstagramToken.SINGLE_ROW_ID)).thenReturn(Optional.ofNullable(rowOrNull));
	}

	@Test
	void 행이_없고_환경변수가_있으면_만료_없이_그대로_저장한다() {
		// given
		givenRow(null);

		// when
		service("ENV").syncFromEnv();

		// then
		ArgumentCaptor<InstagramToken> saved = ArgumentCaptor.forClass(InstagramToken.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getAccessToken()).isEqualTo("ENV");
		assertThat(saved.getValue().getExpiresAt()).isNull();
		assertThat(saved.getValue().getRefreshedAt()).isEqualTo(NOW);
	}

	@Test
	void 저장된_토큰이_환경변수와_같으면_다시_저장하지_않는다() {
		// given
		givenRow(InstagramToken.issue("ENV", EARLIER));

		// when
		service("ENV").syncFromEnv();

		// then
		verify(repository, never()).save(any());
	}

	@Test
	void 환경변수_토큰이_바뀌었으면_기동만으로_덮는다() {
		// given reload를 빠뜨리고 재기동만 한 경우
		InstagramToken row = InstagramToken.issue("STALE", EARLIER);
		givenRow(row);

		// when
		service("ENV").syncFromEnv();

		// then
		verify(repository).save(row);
		assertThat(row.getAccessToken()).isEqualTo("ENV");
		assertThat(row.getRefreshedAt()).isEqualTo(NOW);
	}

	@Test
	void 예전_방식의_행은_기동할_때_새_토큰과_만료_없음으로_바뀐다() {
		// given
		InstagramToken row = legacyRow();
		givenRow(row);

		// when
		service("ENV").syncFromEnv();

		// then
		verify(repository).save(row);
		assertThat(row.getAccessToken()).isEqualTo("ENV");
		assertThat(row.getExpiresAt()).isNull();
	}

	@Test
	void 환경변수가_비면_저장된_토큰을_건드리지_않는다() {
		// when
		service("").syncFromEnv();

		// then
		verify(repository, never()).findById(any());
		verify(repository, never()).save(any());
	}

	@Test
	void 기동_중_저장이_실패해도_예외를_던지지_않는다() {
		// given
		givenRow(null);
		when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

		// when / then
		service("ENV").syncFromEnv();
		verify(repository).save(any());
	}

	@Test
	void currentToken은_저장된_토큰을_돌려준다() {
		// given
		givenRow(InstagramToken.issue("SAVED", NOW));

		// when / then
		assertThat(service("ENV").currentToken()).isEqualTo("SAVED");
	}

	@Test
	void currentToken은_행이_없으면_AUTH_실패다() {
		// given
		givenRow(null);

		// when / then
		assertThatThrownBy(() -> service("ENV").currentToken())
			.isInstanceOfSatisfying(InstagramApiException.class, e -> assertThat(e.getCode()).isEqualTo(190));
	}

	@Test
	void reload는_예전_방식의_행도_환경변수_토큰과_만료_없음으로_덮는다() {
		// given
		InstagramToken row = legacyRow();
		givenRow(row);

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
