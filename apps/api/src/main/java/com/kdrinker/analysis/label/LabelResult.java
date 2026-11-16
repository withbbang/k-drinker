package com.kdrinker.analysis.label;

import com.kdrinker.common.Names;
import com.kdrinker.media.FocusBox;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 라벨·메모에서 읽은 값. 모르는 값은 null. */
public record LabelResult(String drinkName, String category, BigDecimal abv, Integer volumeMl, String breweryName,
		BigDecimal rating, String tasteNotes, String pairing, List<PhotoBox> boxes) {

	private static final BigDecimal FIVE = new BigDecimal("5");
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final BigDecimal TWO = new BigDecimal("2");
	static final int MAX_TEXT = 1000;
	private static final double EPS = 1e-9;
	/** 모델이 술 이름을 못 읽었을 때 지어 넣는 자리표시. 정규화(공백 제거·소문자) 뒤 비교한다. */
	static final Set<String> PLACEHOLDERS = Set.of("모름", "알수없음", "없음", "미상", "불明", "unknown", "n/a", "na",
			"none", "null", "-", "?");

	/**
	 * 모델 출력 검증: 문자열 trim(빈 값 null), 도수는 0 초과 100 미만, 용량은 0 초과, 별점은 0~5의 0.5 단위만
	 * (접수 검증과 같은 범위), 범위 밖 상자·index 제거. 술 이름이 "모름" 같은 자리표시면 null(술 이름을 못 읽음).
	 */
	public LabelResult sanitized(int imageCount) {
		List<PhotoBox> source = boxes == null ? List.of() : boxes;
		return new LabelResult(drinkNameOrNull(drinkName), clean(category), validAbv(abv), validVolume(volumeMl),
				clean(breweryName),
				validRating(rating), capped(tasteNotes), capped(pairing),
				source.stream().filter(b -> b != null && validBox(b, imageCount)).toList());
	}

	/** post.taste_notes·pairing 컬럼(VARCHAR(1000))을 넘지 않게 자른다. 메모(최대 2000자)를 그대로 옮기면 넘칠 수 있다. */
	private static String capped(String s) {
		String t = clean(s);
		if (t == null || t.length() <= MAX_TEXT) {
			return t;
		}
		int end = MAX_TEXT;
		if (Character.isHighSurrogate(t.charAt(end - 1))) {
			end--;
		}
		return t.substring(0, end).strip();
	}

	private static String drinkNameOrNull(String s) {
		String t = clean(s);
		if (t == null) {
			return null;
		}
		boolean placeholder = PLACEHOLDERS.contains(Names.normalize(t))
				|| PLACEHOLDERS.contains(t.toLowerCase(Locale.ROOT));
		return placeholder ? null : t;
	}

	private static String clean(String s) {
		if (s == null) {
			return null;
		}
		String t = s.strip();
		return t.isEmpty() ? null : t;
	}

	private static BigDecimal validAbv(BigDecimal a) {
		return a == null || a.signum() <= 0 || a.compareTo(HUNDRED) >= 0 ? null : a;
	}

	private static Integer validVolume(Integer v) {
		return v == null || v <= 0 ? null : v;
	}

	private static BigDecimal validRating(BigDecimal r) {
		if (r == null || r.signum() < 0 || r.compareTo(FIVE) > 0) {
			return null;
		}
		return r.multiply(TWO).stripTrailingZeros().scale() <= 0 ? r : null;
	}

	private static boolean validBox(PhotoBox b, int imageCount) {
		FocusBox f = b.box();
		return b.index() >= 0 && b.index() < imageCount && f != null
				&& f.x() >= 0 && f.y() >= 0 && f.w() > 0 && f.h() > 0
				&& f.x() + f.w() <= 1 + EPS && f.y() + f.h() <= 1 + EPS;
	}
}
