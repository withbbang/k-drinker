package com.kdrinker.analysis.brewery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** 웹 검색으로 찾은 양조장 정보. 모르는 값은 null. sourceUrl은 실제 검색 결과에 나온 URL이다. */
public record ResearchedBrewery(String name, String address, String phone, String homepage,
		String instagramHandle, String sourceUrl) {

	/** brewery 테이블 컬럼 길이(V2, V4). 넘는 값은 저장 실패 대신 버린다. */
	static final int MAX_SHORT = 255;
	static final int MAX_URL = 1000;

	private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9._]{1,30}$");
	private static final Pattern INSTAGRAM_URL = Pattern.compile(
			"^(?i)(?:https?://)?(?:www\\.)?instagram\\.com/([^/?#]*).*$");

	/**
	 * 저장 전 검증. 인스타그램 계정은 앞의 {@code @}·프로필 URL을 떼고 {@code [A-Za-z0-9._]{1,30}}만, 홈페이지는 http(s) URL만
	 * 남긴다. 컬럼 길이를 넘는 값은 null로 바꾼다(이름이 넘치면 이름이 비어 저장하지 않는다).
	 */
	public ResearchedBrewery sanitized() {
		return new ResearchedBrewery(fit(name, MAX_SHORT), fit(address, MAX_SHORT), fit(phone, MAX_SHORT),
				fit(httpUrl(homepage), MAX_URL), handle(instagramHandle), fit(sourceUrl, MAX_URL));
	}

	private static String fit(String s, int max) {
		return s == null || s.length() > max ? null : s;
	}

	static String handle(String raw) {
		if (raw == null) {
			return null;
		}
		String h = raw.strip();
		var url = INSTAGRAM_URL.matcher(h);
		if (url.matches()) {
			h = url.group(1);
		}
		if (h.startsWith("@")) {
			h = h.substring(1).strip();
		}
		return HANDLE.matcher(h).matches() ? h : null;
	}

	static String httpUrl(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.strip();
		try {
			URI uri = new URI(s);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			boolean http = scheme.equals("http") || scheme.equals("https");
			return http && uri.getHost() != null && !uri.getHost().isEmpty() ? s : null;
		} catch (URISyntaxException e) {
			return null;
		}
	}
}
