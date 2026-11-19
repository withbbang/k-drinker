package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HashtagsTest {

	private static final List<String> FIXED = List.of("#전통주", "#우리술", "#전통주후기", "#막걸리", "#전통주추천", "#막걸리추천");

	private final Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);

	private static LabelResult label(String drinkName) {
		return new LabelResult(drinkName, null, null, null, null, null, null, null, List.of());
	}

	private static DraftTag t(String tag, TagCategory c) {
		return new DraftTag(tag, c);
	}

	private final List<DraftTag> fromClaude = List.of(t("#전통주", TagCategory.PAIRING_TASTE),
			t("#화요 25", TagCategory.NAME), t("#여주", TagCategory.REGION), t("#화요", TagCategory.BREWERY),
			t("#전과막걸리", TagCategory.PAIRING_TASTE), t("#달달", TagCategory.PAIRING_TASTE),
			t("#깔끔", TagCategory.PAIRING_TASTE));

	@Test
	void 고정_태그가_앞에_그대로_오고_이름_양조장은_이름표다() {
		// when
		List<String> all = Hashtags.finalize(fromClaude, label("화요 25"), brewery).all();

		// then
		assertThat(all.subList(0, 6)).containsExactlyElementsOf(FIXED);
		assertThat(all).contains("#{술이름태그}").doesNotContain("#화요25", "#화요 25");
		assertThat(all).containsSubsequence("#{술이름태그}", "#여주", "#{양조장태그}", "#전과막걸리", "#달달");
	}

	@Test
	void 페어링_맛은_두_개만_남고_총_열두_개_이하다() {
		// when
		List<String> all = Hashtags.finalize(fromClaude, label("화요 25"), brewery).all();

		// then
		assertThat(all).contains("#전과막걸리", "#달달").doesNotContain("#깔끔");
		assertThat(all).hasSize(11).hasSizeLessThanOrEqualTo(Hashtags.MAX);
	}

	@Test
	void 양조장이_없으면_지역과_양조장_태그가_빠진다() {
		// when
		List<String> all = Hashtags.finalize(fromClaude, label("화요 25"), null).all();

		// then
		assertThat(all).doesNotContain("#여주", "#화요", "#{양조장태그}").contains("#{술이름태그}");
	}

	@Test
	void 고정_태그가_Claude_쪽에_또_있어도_한_번만_나온다() {
		// given
		List<DraftTag> tags = List.of(t("#막걸리", TagCategory.TYPE), t("#막걸리", TagCategory.PAIRING_TASTE),
				t("전통주후기", TagCategory.PAIRING_TASTE));

		// when
		List<String> all = Hashtags.finalize(tags, label("새술"), null).all();

		// then
		assertThat(all.stream().filter("#막걸리"::equals)).hasSize(1);
		assertThat(all.stream().filter("#전통주후기"::equals)).hasSize(1);
	}

	@Test
	void 특수문자는_지워지고_중복은_하나로_합쳐진다() {
		// given
		List<DraftTag> tags = List.of(t("#전-과.막걸리", TagCategory.PAIRING_TASTE), t("전과막걸리", TagCategory.PAIRING_TASTE),
				t("#.-", TagCategory.PAIRING_TASTE), t(null, TagCategory.PAIRING_TASTE));

		// when
		HashtagSet set = Hashtags.finalize(tags, label("새술"), null);

		// then
		assertThat(set.pairingTaste()).containsExactly("#전과막걸리");
	}

	@Test
	void 술_이름이_있으면_Claude_태그와_상관없이_이름표_태그다() {
		// when
		HashtagSet set = Hashtags.finalize(List.of(), label("새 술 12"), null);

		// then
		assertThat(set.name()).isEqualTo("#{술이름태그}");
		assertThat(set.brewery()).isNull();
	}

	@Test
	void 이름이_전혀_없으면_이름_태그는_없다() {
		// when
		HashtagSet set = Hashtags.finalize(null, label(null), null);

		// then
		assertThat(set.name()).isNull();
		assertThat(set.all()).containsExactlyElementsOf(FIXED);
	}

	@Test
	void 술_종류_태그는_하나만_남는다() {
		// given
		List<DraftTag> tags = List.of(t("#약주", TagCategory.TYPE), t("#청주", TagCategory.TYPE));

		// when
		HashtagSet set = Hashtags.finalize(tags, label("새술"), null);

		// then
		assertThat(set.type()).isEqualTo("#약주");
		assertThat(set.all()).doesNotContain("#청주");
	}

	@Test
	void dropOne은_페어링_맛_뒤에서부터_지역_양조장_순으로_하나씩_뺀다() {
		// given
		HashtagSet set = Hashtags.finalize(fromClaude, label("화요 25"), brewery);

		// when
		HashtagSet s1 = set.dropOne().orElseThrow();
		HashtagSet s2 = s1.dropOne().orElseThrow();
		HashtagSet s3 = s2.dropOne().orElseThrow();
		HashtagSet s4 = s3.dropOne().orElseThrow();

		// then
		assertThat(s1.all()).doesNotContain("#달달").contains("#전과막걸리");
		assertThat(s2.all()).doesNotContain("#전과막걸리").contains("#여주");
		assertThat(s3.all()).doesNotContain("#여주").contains("#{양조장태그}");
		assertThat(s4.all()).doesNotContain("#{양조장태그}").contains("#{술이름태그}");
	}

	@Test
	void 고정과_이름만_남으면_dropOne은_빈_값이다() {
		// given
		HashtagSet set = Hashtags.finalize(List.of(), label("화요25"), null);

		// when
		Optional<HashtagSet> dropped = set.dropOne();

		// then
		assertThat(dropped).isEmpty();
		assertThat(set.all()).contains("#{술이름태그}").hasSize(7);
	}

	@Test
	void 정규화한_태그가_30자를_넘으면_버린다() {
		// given
		String thirty = "가".repeat(29) + "\uD840\uDC00"; // 마지막 한 자는 보충 평면 한자(UTF-16 두 칸)
		String thirtyOne = "가".repeat(31);

		// when / then
		assertThat(Hashtags.normalize(thirty)).isEqualTo("#" + thirty);
		assertThat(Hashtags.normalize("#" + thirtyOne)).isNull();
		assertThat(Hashtags.normalize("가 ".repeat(31))).isNull();
	}

	@Test
	void 너무_긴_이름_태그는_버리고_라벨_이름으로도_만들지_않는다() {
		// given
		String longName = "가".repeat(31);

		// when
		HashtagSet set = Hashtags.finalize(List.of(t(longName, TagCategory.NAME)), label(longName), brewery);

		// then
		assertThat(set.name()).isNull();
	}

	@Test
	void 최대_길이_태그_12개를_이어도_hashtags_컬럼_500자에_들어간다() {
		// given
		String tag = "#" + "가".repeat(Hashtags.MAX_TAG_LENGTH);
		HashtagSet set = new HashtagSet(List.of(tag, tag, tag, tag, tag, tag), tag, tag, tag, tag, List.of(tag, tag));

		// when
		String joined = set.joined();

		// then
		assertThat(set.all()).hasSize(Hashtags.MAX);
		assertThat(joined.codePointCount(0, joined.length())).isLessThanOrEqualTo(500);
		assertThat(joined.length()).isLessThanOrEqualTo(500);
	}
}
