package com.kdrinker.caption;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.drink.entity.Drink;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** 캡션 이름표를 채울 값. 없는 값은 null. */
public record CaptionValues(String drinkName, String category, BigDecimal abv, Integer volumeMl, BigDecimal rating,
		Brewery brewery) {

	public static final CaptionValues EMPTY = new CaptionValues(null, null, null, null, null, null);

	public static CaptionValues of(Drink drinkOrNull, BigDecimal rating) {
		if (drinkOrNull == null) {
			return new CaptionValues(null, null, null, null, rating, null);
		}
		return of(drinkOrNull.getName(), drinkOrNull, drinkOrNull.getBrewery(), rating);
	}

	/** 미리보기·새 술 이름용. 술 이름과 양조장을 따로 받는다. */
	public static CaptionValues of(String drinkName, Drink drinkOrNull, Brewery breweryOrNull, BigDecimal rating) {
		return new CaptionValues(drinkName, drinkOrNull == null ? null : drinkOrNull.getCategory(),
				drinkOrNull == null ? null : drinkOrNull.getAbv(),
				drinkOrNull == null ? null : drinkOrNull.getVolumeMl(), rating, breweryOrNull);
	}

	/** 이름표(중괄호 없이) → 값. 값이 없으면 키도 없다. */
	public Map<String, String> asMap() {
		Map<String, String> m = new HashMap<>();
		put(m, "술이름", drinkName);
		put(m, "술이름태그", tag(drinkName));
		put(m, "종류", category);
		put(m, "도수", abv == null ? null : ClaudeCaptionWriter.abv(abv) + "%");
		put(m, "용량", volumeMl == null ? null : volumeMl + "ml");
		put(m, "별점", rating == null ? null : CaptionAssembler.ratingBlock(rating));
		if (brewery != null) {
			put(m, "양조장", brewery.getName());
			put(m, "양조장태그", tag(brewery.getName()));
			put(m, "주소", brewery.getAddress());
			put(m, "전화", brewery.getPhone());
			put(m, "홈페이지", BreweryBlock.homepage(brewery.getHomepage()));
			String handle = BreweryBlock.handle(brewery.getInstagramHandle());
			put(m, "인스타그램", handle == null || handle.isBlank() ? null : "@" + handle);
			put(m, "기타SNS", brewery.getOtherSns());
		}
		return m;
	}

	private static String tag(String raw) {
		String t = Hashtags.normalize(raw);
		return t == null ? null : t.substring(1);
	}

	private static void put(Map<String, String> m, String key, String value) {
		if (value != null && !value.isBlank()) {
			m.put(key, value.strip());
		}
	}
}
