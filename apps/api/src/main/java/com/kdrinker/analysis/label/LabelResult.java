package com.kdrinker.analysis.label;

import com.kdrinker.media.FocusBox;
import java.math.BigDecimal;
import java.util.List;

/** 라벨·메모에서 읽은 값. 모르는 값은 null. */
public record LabelResult(String drinkName, String category, BigDecimal abv, Integer volumeMl, String breweryName,
		BigDecimal rating, String tasteNotes, String pairing, List<PhotoBox> boxes) {

	private static final BigDecimal FIVE = new BigDecimal("5");
	private static final BigDecimal TWO = new BigDecimal("2");
	private static final double EPS = 1e-9;

	/** 모델 출력 검증: 문자열 trim(빈 값 null), 별점은 0~5의 0.5 단위만, 범위 밖 상자·index 제거. */
	public LabelResult sanitized(int imageCount) {
		List<PhotoBox> source = boxes == null ? List.of() : boxes;
		return new LabelResult(clean(drinkName), clean(category), abv, volumeMl, clean(breweryName),
				validRating(rating), clean(tasteNotes), clean(pairing),
				source.stream().filter(b -> b != null && validBox(b, imageCount)).toList());
	}

	private static String clean(String s) {
		if (s == null) {
			return null;
		}
		String t = s.strip();
		return t.isEmpty() ? null : t;
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
