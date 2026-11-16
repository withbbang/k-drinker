package com.kdrinker.common;

import java.util.regex.Pattern;

/** 인스타그램 계정 정규화. 웹 조사 결과와 CSV 적재가 같은 규칙을 쓴다. */
public final class InstagramHandles {

	private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9._]{1,30}$");
	private static final Pattern PROFILE_URL = Pattern.compile(
			"^(?i)(?:https?://)?(?:www\\.)?instagram\\.com/([^/?#]*).*$");

	private InstagramHandles() {
	}

	/** 앞의 {@code @}·프로필 URL을 떼고 {@code [A-Za-z0-9._]{1,30}}만 남긴다. 맞지 않으면 null. */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String h = raw.strip();
		var url = PROFILE_URL.matcher(h);
		if (url.matches()) {
			h = url.group(1);
		}
		if (h.startsWith("@")) {
			h = h.substring(1).strip();
		}
		return HANDLE.matcher(h).matches() ? h : null;
	}
}
