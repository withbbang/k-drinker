package com.kdrinker.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TokensTest {

	@Test
	void 고정_난수면_43자_base64url_토큰이_결정적으로_나온다() {
		// given
		SecureRandom fixed = new SecureRandom() {
			@Override
			public void nextBytes(byte[] bytes) {
				Arrays.fill(bytes, (byte) 7);
			}
		};

		// when
		String token = Tokens.random(fixed);

		// then
		assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
		assertThat(Tokens.random(fixed)).isEqualTo(token);
	}

}
