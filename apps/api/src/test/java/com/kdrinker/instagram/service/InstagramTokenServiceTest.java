package com.kdrinker.instagram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.instagram.InstagramApiException;
import com.kdrinker.instagram.InstagramClient;
import com.kdrinker.instagram.InstagramClient.RefreshedToken;
import com.kdrinker.instagram.InstagramProperties;
import com.kdrinker.instagram.entity.InstagramToken;
import com.kdrinker.instagram.repository.InstagramTokenRepository;
import com.kdrinker.telegram.TelegramSender;
import com.kdrinker.telegram.entity.NotificationKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InstagramTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-15T16:00:00Z");

	private final InstagramTokenRepository repository = mock(InstagramTokenRepository.class);
	private final InstagramClient client = mock(InstagramClient.class);
	private final TelegramSender sender = mock(TelegramSender.class);
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private InstagramTokenService service(String envToken) {
		InstagramProperties props = new InstagramProperties("https://graph.instagram.com", "v27.0", envToken, "1789",
				Duration.ofSeconds(3), Duration.ofMinutes(5), Duration.ofDays(30), "0 0 4 * * *",
				ZoneId.of("Asia/Seoul"));
		return new InstagramTokenService(repository, client, sender, clock, props);
	}

	private InstagramToken row(Instant expiresAt, Instant refreshedAt) {
		return InstagramToken.issue("OLD", expiresAt, refreshedAt);
	}

	@Test
	void 행이_없고_환경변수가_있으면_갱신해_실제_만료를_저장한다() {
		// given
		when(repository.existsById(InstagramToken.SINGLE_ROW_ID)).thenReturn(false);
		when(client.refresh("ENV")).thenReturn(new RefreshedToken("NEW", 5_184_000L));

		// when
		service("ENV").initIfMissing();

		// then
		ArgumentCaptor<InstagramToken> saved = ArgumentCaptor.forClass(InstagramToken.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getAccessToken()).isEqualTo("NEW");
		assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(60)));
		assertThat(saved.getValue().getRefreshedAt()).isEqualTo(NOW);
	}

	@Test
	void 갱신이_실패하면_환경변수_토큰을_1일_뒤_만료로_저장한다() {
		// given
		when(repository.existsById(InstagramToken.SINGLE_ROW_ID)).thenReturn(false);
		when(client.refresh("ENV")).thenThrow(new InstagramApiException(100, null, "too new"));

		// when
		service("ENV").initIfMissing();

		// then
		ArgumentCaptor<InstagramToken> saved = ArgumentCaptor.forClass(InstagramToken.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getAccessToken()).isEqualTo("ENV");
		assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
	}

	@Test
	void 전송_실패와_190도_만료일을_지어내지_않고_1일로_저장한다() {
		for (int code : new int[] { -1, 190 }) {
			// given
			InstagramTokenRepository repo = mock(InstagramTokenRepository.class);
			InstagramClient failing = mock(InstagramClient.class);
			when(repo.existsById(InstagramToken.SINGLE_ROW_ID)).thenReturn(false);
			when(failing.refresh("ENV")).thenThrow(new InstagramApiException(code, null, "fail"));
			InstagramProperties props = new InstagramProperties("b", "v27.0", "ENV", "1", Duration.ofSeconds(3),
					Duration.ofMinutes(5), Duration.ofDays(30), "0 0 4 * * *", ZoneId.of("Asia/Seoul"));

			// when
			new InstagramTokenService(repo, failing, sender, clock, props).initIfMissing();

			// then
			ArgumentCaptor<InstagramToken> saved = ArgumentCaptor.forClass(InstagramToken.class);
			verify(repo).save(saved.capture());
			assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
		}
	}

	@Test
	void 행이_있으면_환경변수를_쓰지_않는다() {
		// given
		when(repository.existsById(InstagramToken.SINGLE_ROW_ID)).thenReturn(true);

		// when
		service("ENV").initIfMissing();

		// then
		verify(client, never()).refresh(any());
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
	void 만료까지_40일이_남으면_갱신하지_않는다() {
		// given
		when(repository.findById(InstagramToken.SINGLE_ROW_ID))
			.thenReturn(Optional.of(row(NOW.plus(Duration.ofDays(40)), NOW.minus(Duration.ofDays(20)))));

		// when
		service("ENV").refreshIfDue();

		// then
		verify(client, never()).refresh(any());
	}

	@Test
	void 만료가_가까우면_갱신하고_저장한다() {
		// given
		InstagramToken row = row(NOW.plus(Duration.ofDays(20)), NOW.minus(Duration.ofDays(40)));
		when(repository.findById(InstagramToken.SINGLE_ROW_ID)).thenReturn(Optional.of(row));
		when(client.refresh("OLD")).thenReturn(new RefreshedToken("NEW", 5_184_000L));

		// when
		service("ENV").refreshIfDue();

		// then
		verify(repository).save(row);
		assertThat(row.getAccessToken()).isEqualTo("NEW");
		assertThat(row.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(60)));
	}

	@Test
	void 마지막_갱신_후_24시간이_안_지났으면_갱신하지_않는다() {
		// given
		when(repository.findById(InstagramToken.SINGLE_ROW_ID))
			.thenReturn(Optional.of(row(NOW.plus(Duration.ofDays(20)), NOW.minus(Duration.ofHours(3)))));

		// when
		service("ENV").refreshIfDue();

		// then
		verify(client, never()).refresh(any());
	}

	@Test
	void 갱신_실패_알림은_같은_날_같은_키다() {
		// given
		when(repository.findById(InstagramToken.SINGLE_ROW_ID))
			.thenReturn(Optional.of(row(NOW.plus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(50)))));
		when(client.refresh("OLD")).thenThrow(new InstagramApiException(190, null, "expired"));
		InstagramTokenService service = service("ENV");

		// when
		service.refreshIfDue();
		service.refreshIfDue();

		// then
		verify(sender, times(2)).send(eq("TOKEN_REFRESH_FAILED:2026-11-16"), eq(NotificationKind.TOKEN_REFRESH_FAILED),
				isNull(), any());
		verify(repository, never()).save(any());
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
	void reload는_환경변수로_덮고_갱신한다() {
		// given
		InstagramToken row = row(NOW.plus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(59)));
		when(repository.findById(InstagramToken.SINGLE_ROW_ID)).thenReturn(Optional.of(row));
		when(client.refresh("ENV")).thenReturn(new RefreshedToken("NEW", 5_184_000L));

		// when
		TokenStatus status = service("ENV").reload();

		// then
		assertThat(row.getAccessToken()).isEqualTo("NEW");
		assertThat(status.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(60)));
		assertThat(status.refreshedAt()).isEqualTo(NOW);
	}

	@Test
	void reload는_환경변수가_비면_BusinessException이다() {
		assertThatThrownBy(() -> service("").reload()).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSTAGRAM_TOKEN_MISSING));
	}

}
