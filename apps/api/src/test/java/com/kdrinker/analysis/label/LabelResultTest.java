package com.kdrinker.analysis.label;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.media.FocusBox;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabelResultTest {

	private static LabelResult withRating(String rating) {
		return new LabelResult("술", "탁주", null, null, "양조장", rating == null ? null : new BigDecimal(rating),
				null, null, List.of());
	}

	private static LabelResult withBoxes(PhotoBox... boxes) {
		return new LabelResult(null, null, null, null, null, null, null, null, List.of(boxes));
	}

	@Test
	void 별점은_0에서_5사이_0점5_단위만_남긴다() {
		assertThat(withRating("4.3").sanitized(1).rating()).isNull();
		assertThat(withRating("4.5").sanitized(1).rating()).isEqualByComparingTo("4.5");
		assertThat(withRating("5.5").sanitized(1).rating()).isNull();
		assertThat(withRating("-0.5").sanitized(1).rating()).isNull();
		assertThat(withRating("0").sanitized(1).rating()).isEqualByComparingTo("0");
		assertThat(withRating("5.0").sanitized(1).rating()).isEqualByComparingTo("5");
		assertThat(withRating(null).sanitized(1).rating()).isNull();
	}

	@Test
	void 범위를_벗어난_상자와_범위밖_index는_제거한다() {
		// given
		LabelResult result = withBoxes(
				new PhotoBox(0, new FocusBox(0.8, 0.1, 0.4, 0.5)),
				new PhotoBox(0, new FocusBox(0.1, 0.1, 0.5, 0.8)),
				new PhotoBox(3, new FocusBox(0.1, 0.1, 0.5, 0.8)),
				new PhotoBox(-1, new FocusBox(0.1, 0.1, 0.5, 0.8)),
				new PhotoBox(1, new FocusBox(0.1, 0.1, 0, 0.5)),
				new PhotoBox(1, new FocusBox(-0.1, 0.1, 0.5, 0.5)),
				new PhotoBox(1, new FocusBox(0.0, 0.0, 1.0, 1.0)));

		// when
		List<PhotoBox> boxes = result.sanitized(2).boxes();

		// then
		assertThat(boxes).containsExactly(
				new PhotoBox(0, new FocusBox(0.1, 0.1, 0.5, 0.8)),
				new PhotoBox(1, new FocusBox(0.0, 0.0, 1.0, 1.0)));
	}

	@Test
	void 부동소수_오차_정도는_상자로_인정한다() {
		LabelResult result = withBoxes(new PhotoBox(0, new FocusBox(0.7, 0.1, 0.1 + 0.2, 0.5)));

		assertThat(result.sanitized(1).boxes()).hasSize(1);
	}

	@Test
	void 문자열은_trim하고_빈_값은_null이다() {
		// given
		LabelResult result = new LabelResult("  ", " 막걸리 ", null, null, "", null, "\t", " 전 ", null);

		// when
		LabelResult s = result.sanitized(1);

		// then
		assertThat(s.drinkName()).isNull();
		assertThat(s.category()).isEqualTo("막걸리");
		assertThat(s.breweryName()).isNull();
		assertThat(s.tasteNotes()).isNull();
		assertThat(s.pairing()).isEqualTo("전");
		assertThat(s.boxes()).isEmpty();
	}

	private static LabelResult withSpec(String abv, Integer volumeMl) {
		return new LabelResult("술", null, abv == null ? null : new BigDecimal(abv), volumeMl, null, null, null, null,
				List.of());
	}

	@Test
	void 도수는_0보다_크고_100보다_작을_때만_남긴다() {
		assertThat(withSpec("0", null).sanitized(1).abv()).isNull();
		assertThat(withSpec("-1", null).sanitized(1).abv()).isNull();
		assertThat(withSpec("100", null).sanitized(1).abv()).isNull();
		assertThat(withSpec("0.1", null).sanitized(1).abv()).isEqualByComparingTo("0.1");
		assertThat(withSpec("99.9", null).sanitized(1).abv()).isEqualByComparingTo("99.9");
		assertThat(withSpec(null, null).sanitized(1).abv()).isNull();
	}

	@Test
	void 용량은_0보다_클_때만_남긴다() {
		assertThat(withSpec(null, 0).sanitized(1).volumeMl()).isNull();
		assertThat(withSpec(null, -750).sanitized(1).volumeMl()).isNull();
		assertThat(withSpec(null, 1).sanitized(1).volumeMl()).isEqualTo(1);
		assertThat(withSpec(null, null).sanitized(1).volumeMl()).isNull();
	}

	@Test
	void 맛노트와_페어링은_컬럼_길이_1000자까지만_남긴다() {
		// given
		LabelResult result = new LabelResult("술", null, null, null, null, null, "맛".repeat(1001), "전".repeat(1000),
				List.of());

		// when
		LabelResult sanitized = result.sanitized(1);

		// then
		assertThat(sanitized.tasteNotes()).isEqualTo("맛".repeat(1000));
		assertThat(sanitized.pairing()).isEqualTo("전".repeat(1000));
	}

	private static LabelResult withDrinkName(String drinkName) {
		return new LabelResult(drinkName, null, null, null, null, null, null, null, List.of());
	}

	@Test
	void 술_이름이_모름_같은_자리표시면_null이다() {
		for (String placeholder : List.of("모름", "알수없음", "알 수 없음", "없음", "미상", "불明", "unknown", "n/a", "na",
				"none", "null", "-", "?", " Unknown ", "N/A", "NULL", "알 수\u00A0없음")) {
			assertThat(withDrinkName(placeholder).sanitized(1).drinkName()).as(placeholder).isNull();
		}
	}

	@Test
	void 자리표시가_들어간_진짜_술_이름은_남긴다() {
		assertThat(withDrinkName("없음막걸리").sanitized(1).drinkName()).isEqualTo("없음막걸리");
		assertThat(withDrinkName(" 화요 25 ").sanitized(1).drinkName()).isEqualTo("화요 25");
		assertThat(withDrinkName("Nana").sanitized(1).drinkName()).isEqualTo("Nana");
	}
}
