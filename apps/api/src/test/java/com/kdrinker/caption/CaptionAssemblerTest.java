package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptionAssemblerTest {

	private static final String TAGS = "#전통주 #우리술";
	private final Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);

	private static CaptionDraft draft(String tasting) {
		return new CaptionDraft("훅이에요", "소개예요", tasting, List.of());
	}

	@Test
	void 모든_블록이_순서대로_빈_줄_하나로_나뉜다() {
		// when
		String stored = CaptionAssembler.assemble(draft("달았어요."), new BigDecimal("4.5"), brewery, TAGS);

		// then
		assertThat(stored).isEqualTo(
				"훅이에요\n\n소개예요\n\n달았어요.\n\n★4.5\n\n<!--brewery-->\n🍶 화요\n<!--brewery-->\n\n#전통주 #우리술");
	}

	@Test
	void 별점이_없으면_별점_블록과_빈_줄이_없다() {
		// when
		String stored = CaptionAssembler.assemble(draft("달았어요."), null, brewery, TAGS);

		// then
		assertThat(stored).doesNotContain("★").doesNotContain("\n\n\n");
	}

	@Test
	void 정수_별점은_소수_한_자리로_쓴다() {
		assertThat(CaptionAssembler.assemble(draft("a"), new BigDecimal("4"), null, TAGS)).contains("★4.0");
	}

	@Test
	void tasting이_비면_그_블록과_빈_줄이_없다() {
		// when
		String stored = CaptionAssembler.assemble(draft(" "), null, null, TAGS);

		// then
		assertThat(stored).isEqualTo("훅이에요\n\n소개예요\n\n<!--brewery-->\n<!--brewery-->\n\n#전통주 #우리술");
	}

	@Test
	void 양조장이_없어도_마커를_지운_결과에_연속_빈_줄이_없다() {
		// when
		String shown = CaptionAssembler.stripMarkers(CaptionAssembler.assemble(draft("달았어요."), new BigDecimal("4.5"), null, TAGS));

		// then
		assertThat(shown).doesNotContain("\n\n\n").doesNotContain("<!--");
		assertThat(shown).isEqualTo("훅이에요\n\n소개예요\n\n달았어요.\n\n★4.5\n\n#전통주 #우리술");
	}

	@Test
	void 양조장이_있으면_마커만_지워지고_블록은_남는다() {
		// when
		String shown = CaptionAssembler.stripMarkers(CaptionAssembler.assemble(draft("달았어요."), null, brewery, TAGS));

		// then
		assertThat(shown).doesNotContain("\n\n\n").doesNotContain("<!--");
		assertThat(shown).isEqualTo("훅이에요\n\n소개예요\n\n달았어요.\n\n🍶 화요\n\n#전통주 #우리술");
	}

	@Test
	void 해시태그가_없고_양조장도_없으면_끝에_빈_줄이_남지_않는다() {
		// when
		String shown = CaptionAssembler.stripMarkers(CaptionAssembler.assemble(draft("a"), null, null, ""));

		// then
		assertThat(shown).isEqualTo("훅이에요\n\n소개예요\n\na");
	}

	@Test
	void 길이는_마커를_빼고_코드_포인트로_센다() {
		// given
		String stored = "🍶\n\n<!--brewery-->\n<!--brewery-->";

		// when
		int length = CaptionAssembler.length(stored, CaptionValues.EMPTY);

		// then
		assertThat(length).isEqualTo(1);
	}

	@Test
	void 렌더는_이름표를_채우고_마커를_뺀다() {
		// given
		String stored = "별점은 {별점}\n\n<!--brewery-->\n<!--brewery-->";
		CaptionValues values = CaptionValues.of(null, new BigDecimal("4"));

		// when / then
		assertThat(CaptionAssembler.render(stored, values)).isEqualTo("별점은 ★4.0");
		assertThat(CaptionAssembler.length(stored, values)).isEqualTo(8);
	}

	@Test
	void 이름표가_없는_캡션의_렌더는_마커만_뺀다() {
		assertThat(CaptionAssembler.render("본문\n\n<!--brewery-->\n<!--brewery-->", CaptionValues.EMPTY)).isEqualTo("본문");
	}
}
