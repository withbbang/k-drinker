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
	void assemble_usesPlaceholders() {
		// when
		String stored = CaptionAssembler.assemble(draft("달았어요."), TAGS);

		// then
		assertThat(stored).isEqualTo("훅이에요\n\n소개예요\n\n달았어요.\n\n{별점}\n\n" + BreweryBlock.TEMPLATE + "\n\n#전통주 #우리술");
	}

	@Test
	void tasting이_비면_그_블록과_빈_줄이_없다() {
		// when
		String stored = CaptionAssembler.assemble(draft(" "), TAGS);

		// then
		assertThat(stored).isEqualTo("훅이에요\n\n소개예요\n\n{별점}\n\n" + BreweryBlock.TEMPLATE + "\n\n#전통주 #우리술");
	}

	@Test
	void 채우면_별점이_없을_때_별점_줄과_빈_줄이_빠진다() {
		// given
		String stored = CaptionAssembler.assemble(draft("달았어요."), TAGS);

		// when
		String shown = CaptionAssembler.render(stored, CaptionValues.EMPTY);

		// then
		assertThat(shown).doesNotContain("★").doesNotContain("\n\n\n").doesNotContain("{");
	}

	@Test
	void 채우면_별점과_양조장_블록이_나온다() {
		// given
		String stored = CaptionAssembler.assemble(draft("달았어요."), TAGS);

		// when
		String shown = CaptionAssembler.render(stored, CaptionValues.of(null, null, brewery, new BigDecimal("4")));

		// then
		assertThat(shown).isEqualTo("훅이에요\n\n소개예요\n\n달았어요.\n\n★4.0\n\n🍶 화요\n\n#전통주 #우리술");
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
