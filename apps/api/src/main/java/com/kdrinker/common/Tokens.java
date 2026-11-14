package com.kdrinker.common;

import java.security.SecureRandom;
import java.util.Base64;

/** 추측할 수 없는 랜덤 토큰 생성. 난수원은 주입받은 SecureRandom만 쓴다. */
public final class Tokens {

	private static final int TOKEN_BYTES = 32;

	private Tokens() {
	}

	/** 32바이트 난수를 패딩 없는 base64url(43자)로 돌려준다. */
	public static String random(SecureRandom secureRandom) {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
