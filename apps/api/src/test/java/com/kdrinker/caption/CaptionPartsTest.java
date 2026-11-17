package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CaptionPartsTest {

	@Test
	void 마커_앞뒤로_나눈다() {
		// given
		String stored = "훅\n\n소개\n\n★4.5\n\n" + BreweryBlock.render(null) + "\n\n#막걸리 #전통주";

		// when
		CaptionParts parts = CaptionParts.split(stored);

		// then
		assertThat(parts.head()).isEqualTo("훅\n\n소개\n\n★4.5");
		assertThat(parts.tail()).isEqualTo("#막걸리 #전통주");
	}

	@Test
	void 마커가_없으면_전체가_머리다() {
		// when
		CaptionParts parts = CaptionParts.split("  옛 캡션 #태그  ");

		// then
		assertThat(parts.head()).isEqualTo("옛 캡션 #태그");
		assertThat(parts.tail()).isEmpty();
	}

	@Test
	void null은_빈_두_조각이다() {
		// when
		CaptionParts parts = CaptionParts.split(null);

		// then
		assertThat(parts).isEqualTo(new CaptionParts("", ""));
	}

	@Test
	void 빈_조각은_빼고_블록과_잇는다() {
		// when
		String joined = new CaptionParts("", "").join(null);

		// then
		assertThat(joined).isEqualTo(BreweryBlock.render(null));
	}

	@Test
	void 나눈_뒤_다시_이으면_원래_캡션이다() {
		// given
		Brewery brewery = Brewery.of("국순당", "국순당", BrewerySource.PUBLIC_DATA);
		String stored = CaptionAssembler.assemble(new CaptionDraft("훅", "소개", "맛", null),
				new BigDecimal("4.5"), brewery, "#막걸리");

		// when
		String joined = CaptionParts.split(stored).join(brewery);

		// then
		assertThat(joined).isEqualTo(stored);
	}
}
